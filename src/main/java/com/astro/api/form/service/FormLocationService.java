package com.astro.api.form.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.form.dto.request.FormLocationReadingRequest;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormLocationCheck;
import com.astro.api.form.model.FormLocationPolicy;
import com.astro.api.form.model.FormLocationReading;
import com.astro.api.form.model.FormScope;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormLocationService {
    private static final int UNIT_RADIUS_METERS = 100;
    private static final int MAX_READING_AGE_SECONDS = 120;
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private final UnitRepository unitRepository;

    public FormLocationService(UnitRepository unitRepository) { this.unitRepository = unitRepository; }

    public FormLocationPolicy policy(Form form) {
        boolean enforceUnitRadius = form.getUnitId() != null;
        return new FormLocationPolicy(true, enforceUnitRadius, enforceUnitRadius ? UNIT_RADIUS_METERS : null,
                enforceUnitRadius ? MAX_READING_AGE_SECONDS : null);
    }

    @Transactional(readOnly = true)
    public FormLocationCheck validate(Form form, FormScope formScope,
                                      List<FormLocationReadingRequest> locationReadings, Instant submittedAt) {
        if (form.getUnitId() == null) return null;
        UnitLocationProjection unitLocation = unitRepository.findLocationByIdAndWorkspaceId(form.getUnitId(), formScope.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade vinculada não encontrada no workspace"));
        if (!Boolean.TRUE.equals(unitLocation.getActive())) throw new BusinessException("A unidade vinculada deve estar ativa");
        if (!validCoordinates(unitLocation.getLatitude(), unitLocation.getLongitude())) {
            throw new BusinessException("A unidade vinculada não possui geolocalização válida cadastrada");
        }
        if (locationReadings == null || locationReadings.isEmpty()) throw new BusinessException("Envie a localização atual para validar a unidade");
        // Tied timestamps use the farthest reading, so reordering cannot hide an out-of-range current position.
        FormLocationReadingRequest currentReading = locationReadings.stream().max(
                Comparator.comparing(FormLocationReadingRequest::recordedAt).thenComparingDouble(reading ->
                        distanceMeters(unitLocation.getLatitude(), unitLocation.getLongitude(), reading.latitude(), reading.longitude())))
                .orElseThrow(() -> new BusinessException("Envie a localização atual para validar a unidade"));
        if (currentReading.recordedAt().isBefore(submittedAt.minusSeconds(MAX_READING_AGE_SECONDS))) {
            throw new BusinessException("Atualize o GPS: a localização deve ter sido registrada nos últimos 2 minutos");
        }
        double distanceMeters = distanceMeters(unitLocation.getLatitude(), unitLocation.getLongitude(),
                currentReading.latitude(), currentReading.longitude());
        if (distanceMeters > UNIT_RADIUS_METERS) throw new BusinessException("Você está fora do raio de 100 metros da unidade vinculada");
        FormLocationReading validatedReading = new FormLocationReading(currentReading.latitude(), currentReading.longitude(),
                currentReading.accuracyMeters(), currentReading.recordedAt());
        return new FormLocationCheck(unitLocation.getUnitId(), unitLocation.getLatitude(), unitLocation.getLongitude(),
                UNIT_RADIUS_METERS, distanceMeters, validatedReading);
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private double distanceMeters(double unitLatitude, double unitLongitude, double userLatitude, double userLongitude) {
        double latitudeDelta = Math.toRadians(userLatitude - unitLatitude);
        double longitudeDelta = Math.toRadians(userLongitude - unitLongitude);
        double latitudeSine = Math.sin(latitudeDelta / 2);
        double longitudeSine = Math.sin(longitudeDelta / 2);
        double haversine = latitudeSine * latitudeSine + Math.cos(Math.toRadians(unitLatitude))
                * Math.cos(Math.toRadians(userLatitude)) * longitudeSine * longitudeSine;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.clamp(haversine, 0.0, 1.0)));
    }
}

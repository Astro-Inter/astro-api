package com.astro.api.form;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.form.dto.request.FormLocationReadingRequest;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormLocationCheck;
import com.astro.api.form.model.FormScope;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormLocationService;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormLocationServiceTest {
    private final UnitRepository unitRepository = mock(UnitRepository.class);
    private final UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
    private final FormLocationService formLocationService = new FormLocationService(unitRepository);
    private final Instant now = Instant.parse("2026-10-24T14:00:00Z");
    private final FormScope formScope = new FormScope(20L, 1L, false);
    private Form form;

    @BeforeEach void setUp() {
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING); form.setUnitId(3L);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getUnitId()).thenReturn(3L); when(unitLocation.getActive()).thenReturn(true);
        when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
    }

    FormLocationReadingRequest reading(double distanceMeters, Instant recordedAt) {
        return new FormLocationReadingRequest(0.0, Math.toDegrees(distanceMeters / 6_371_008.8), 5.0, recordedAt);
    }

    @ParameterizedTest @ValueSource(doubles = {0, 50, 99.9, 100})
    void acceptsPositionsInsideOrAtTheRadius(double distanceMeters) {
        FormLocationCheck locationCheck = formLocationService.validate(form, formScope, List.of(reading(distanceMeters, now)), now);
        assertEquals(distanceMeters, locationCheck.distanceMeters(), 0.000001);
        assertEquals(100, locationCheck.radiusMeters()); assertEquals(3L, locationCheck.unitId());
        assertEquals(0.0, locationCheck.unitLatitude()); assertEquals(now, locationCheck.reading().recordedAt());
    }

    @ParameterizedTest @ValueSource(doubles = {100.001, 101, 500, 10000})
    void blocksPositionsAboveTheRadius(double distanceMeters) {
        assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(reading(distanceMeters, now)), now));
    }

    @Test void usesMostRecentTimestampRatherThanArrayOrderOrAnOldNearPosition() {
        FormLocationReadingRequest near = reading(10, now.minusSeconds(20));
        FormLocationReadingRequest far = reading(150, now);
        for (List<FormLocationReadingRequest> readings : List.of(List.of(near, far), List.of(far, near))) {
            assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, readings, now));
        }
        assertDoesNotThrow(() -> formLocationService.validate(form, formScope,
                List.of(reading(150, now.minusSeconds(20)), reading(10, now)), now));
    }

    @Test void tiedTimestampsCannotHideAnOutOfRangePosition() {
        for (List<FormLocationReadingRequest> readings : List.of(List.of(reading(10, now), reading(150, now)),
                List.of(reading(150, now), reading(10, now)))) {
            assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, readings, now));
        }
    }

    @Test void requiresCurrentGpsAndNeverExpandsTheRadiusUsingReportedAccuracy() {
        assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(reading(0, now.minusSeconds(121))), now));
        assertDoesNotThrow(() -> formLocationService.validate(form, formScope, List.of(reading(0, now.minusSeconds(120))), now));
        FormLocationReadingRequest imprecise = new FormLocationReadingRequest(0.0, Math.toDegrees(150 / 6_371_008.8), 10000.0, now);
        assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(imprecise), now));
    }

    @Test void requiresActiveUnitWithValidCoordinatesAndNeverUsesAnotherWorkspace() {
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> formLocationService.validate(form, formScope, List.of(reading(0, now)), now));
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getActive()).thenReturn(false);
        assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(reading(0, now)), now));
        when(unitLocation.getActive()).thenReturn(true);
        for (Double latitude : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, 91.0}) {
            when(unitLocation.getLatitude()).thenReturn(latitude);
            assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(reading(0, now)), now));
        }
        when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(181.0);
        assertThrows(BusinessException.class, () -> formLocationService.validate(form, formScope, List.of(reading(0, now)), now));
        verify(unitRepository, atLeastOnce()).findLocationByIdAndWorkspaceId(form.getUnitId(), formScope.workspaceId());
    }

    @Test void formsWithoutUnitKeepCaptureWithoutDistanceChecks() {
        form.setUnitId(null); clearInvocations(unitRepository);
        assertNull(formLocationService.validate(form, formScope, List.of(), now));
        assertFalse(formLocationService.policy(form).enforceUnitRadius());
        assertNull(formLocationService.policy(form).radiusMeters()); verifyNoInteractions(unitRepository);
        form.setUnitId(3L); assertTrue(formLocationService.policy(form).enforceUnitRadius());
        assertEquals(100, formLocationService.policy(form).radiusMeters());
        assertEquals(120, formLocationService.policy(form).maxReadingAgeSeconds());
    }

    @Test void measuresGeographicMetersAcrossTheDateLine() {
        when(unitLocation.getLongitude()).thenReturn(179.9999);
        FormLocationReadingRequest position = new FormLocationReadingRequest(0.0, -179.9999, 5.0, now);
        assertEquals(22.239, formLocationService.validate(form, formScope, List.of(position), now).distanceMeters(), 0.01);
    }
}

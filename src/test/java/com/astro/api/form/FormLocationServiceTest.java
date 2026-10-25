package com.astro.api.form;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormLocationService;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormLocationServiceTest {
    private final UnitRepository unitRepository = mock(UnitRepository.class);
    private final UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
    private final FormLocationService formLocationService = new FormLocationService(unitRepository);
    private Form form;

    @BeforeEach void setUp() {
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING);
        form.setUnitId(3L);
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
        when(unitLocation.getLatitude()).thenReturn(-23.5);
        when(unitLocation.getLongitude()).thenReturn(-46.6);
    }

    @Test void returnsTheLinkedUnitCoordinatesWithinTheFormWorkspace() {
        UnitLocationProjection result = formLocationService.findUnitLocation(form);
        assertEquals(-23.5, result.getLatitude()); assertEquals(-46.6, result.getLongitude());
        verify(unitRepository).findLocationByIdAndWorkspaceId(3L, 1L);
    }

    @Test void missingOrInvalidCoordinatesAreRepresentedAsNullWithoutBlockingTheForm() {
        for (Double latitude : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, 91.0}) {
            when(unitLocation.getLatitude()).thenReturn(latitude);
            assertNull(formLocationService.findUnitLocation(form));
        }
        when(unitLocation.getLatitude()).thenReturn(-23.5);
        for (Double longitude : new Double[]{null, Double.NaN, Double.NEGATIVE_INFINITY, -181.0}) {
            when(unitLocation.getLongitude()).thenReturn(longitude);
            assertNull(formLocationService.findUnitLocation(form));
        }
        when(unitRepository.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.empty());
        assertNull(formLocationService.findUnitLocation(form));
    }

    @Test void formsWithoutUnitDoNotQueryTheUnitRepository() {
        form.setUnitId(null); clearInvocations(unitRepository);
        assertNull(formLocationService.findUnitLocation(form)); verifyNoInteractions(unitRepository);
        assertFalse(formLocationService.policy(form).captureRequired());
        assertFalse(formLocationService.policy(form).enforceUnitRadius());
        assertNull(formLocationService.policy(form).radiusMeters());
    }

    @Test void exposesAReferenceRadiusWithoutServerEnforcementOrReadingAgeLimit() {
        assertFalse(formLocationService.policy(form).captureRequired());
        assertFalse(formLocationService.policy(form).enforceUnitRadius());
        assertEquals(100, formLocationService.policy(form).radiusMeters());
        assertNull(formLocationService.policy(form).maxReadingAgeSeconds());
    }
}

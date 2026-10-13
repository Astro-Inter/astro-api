package com.astro.api.user.mapper;

import com.astro.api.user.repository.CollaboratorSummaryProjection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaboratorAvatarMapperTest {
    @ParameterizedTest
    @CsvSource({"Bruno Martins,BM", "Ana da Silva,AS", "  álvaro   góes  ,ÁG", "Bruno,B"})
    void suppliesNameInitialsForTheFallbackWithoutReplacingThePhotoUrl(String name, String initials) {
        var row = mock(CollaboratorSummaryProjection.class);
        when(row.getName()).thenReturn(name);
        when(row.getStatus()).thenReturn("ATIVO");
        var mapper = new CollaboratorMapper();
        var withoutPhoto = mapper.toSummary(row, null);
        assertNull(withoutPhoto.profilePhotoUrl());
        assertEquals(initials, withoutPhoto.avatarInitials());
        var withPhoto = mapper.toSummary(row, "https://r2.example.com/foto.jpg");
        assertEquals("https://r2.example.com/foto.jpg", withPhoto.profilePhotoUrl());
        assertEquals(initials, withPhoto.avatarInitials());
    }
}

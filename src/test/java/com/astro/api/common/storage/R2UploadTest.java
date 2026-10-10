package com.astro.api.common.storage;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.config.R2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class R2UploadTest {
    private S3Client client;
    private ObjectProvider<S3Client> provider;
    private R2StorageService storage;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(S3Client.class);
        provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(client);
        storage = new R2StorageService(null, new R2Properties(
                "https://account.r2.cloudflarestorage.com", "photos",
                "test-access-key", "test-secret-key", Duration.ofMinutes(15)), provider);
    }

    @Test
    void shouldUploadOriginalBytesWithDetectedContentTypeAndUniqueUserKey() throws Exception {
        byte[][] contents = {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}
        };
        String[] extensions = {"jpg", "png", "webp"};
        String[] types = {"image/jpeg", "image/png", "image/webp"};
        for (int i = 0; i < contents.length; i++) {
            String path = storage.uploadProfilePhoto(20L,
                    new MockMultipartFile("file", "../../nome-inseguro.jpg", "application/octet-stream", contents[i]));
            var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
            var body = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
            verify(client).putObject(request.capture(), body.capture());
            assertEquals("photos", request.getValue().bucket());
            assertEquals(path, request.getValue().key());
            assertTrue(path.matches("usuarios/20/[a-f0-9-]{36}\\." + extensions[i]));
            assertEquals(types[i], request.getValue().contentType());
            try (var input = body.getValue().contentStreamProvider().newStream()) {
                assertArrayEquals(contents[i], input.readAllBytes());
            }
            clearInvocations(client);
        }
    }

    @Test
    void shouldRejectMissingEmptyAndDisguisedNonImageBeforeAccessingStorage() {
        assertThrows(BusinessException.class, () -> storage.uploadProfilePhoto(20L, null));
        assertThrows(BusinessException.class, () -> storage.uploadProfilePhoto(20L,
                new MockMultipartFile("file", new byte[0])));
        assertThrows(BusinessException.class, () -> storage.uploadProfilePhoto(20L,
                new MockMultipartFile("file", "foto.jpg", "image/jpeg", "texto".getBytes())));
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectOversizeEvenWhenReportedSizeIsIncorrect() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertThrows(BusinessException.class, () -> storage.uploadProfilePhoto(20L, file));
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[5 * 1024 * 1024 + 1]));
        assertThrows(BusinessException.class, () -> storage.uploadProfilePhoto(20L, file));
        verifyNoInteractions(client);
    }

    @Test
    void shouldDeleteObjectFromConfiguredBucket() {
        storage.deleteObject("usuarios/20/anterior.jpg");
        verify(client).deleteObject(DeleteObjectRequest.builder()
                .bucket("photos").key("usuarios/20/anterior.jpg").build());
    }

    @Test
    void shouldPropagateStorageFailure() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("Storage indisponível"));
        assertThrows(IllegalStateException.class, () -> storage.uploadProfilePhoto(20L,
                new MockMultipartFile("file", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})));
    }
}

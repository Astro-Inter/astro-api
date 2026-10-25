package com.astro.api.form.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.config.R2Properties;
import com.astro.api.form.dto.internal.FormImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormImageStorageServiceTest {
    S3Client client = mock(S3Client.class);
    @SuppressWarnings("unchecked") ObjectProvider<S3Client> clients = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") ObjectProvider<S3Presigner> presigners = mock(ObjectProvider.class);
    R2Properties properties = new R2Properties("https://example.r2.cloudflarestorage.com", "avatars", "events",
            "test-access-key", "test-secret-key", Duration.ofDays(1));
    FormImageStorageService service;
    @BeforeEach void setUp() {
        when(clients.getObject()).thenReturn(client);
        service = new FormImageStorageService(clients, presigners, properties, "test-form-images");
    }
    byte[] png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output); return output.toByteArray();
    }

    @Test void rejectsFakeAndTruncatedImagesBeforeUploading() {
        for (byte[] bytes : new byte[][] {"<html>not a photo</html>".getBytes(), new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff}}) {
            assertThrows(BusinessException.class, () -> service.upload("form", 1L,
                    new MockMultipartFile("files", "photo.jpg", "image/jpeg", bytes)));
        }
        verifyNoInteractions(client);
    }

    @Test void detectsRealFormatAndUsesDedicatedConfiguredBucketAndGeneratedKey() throws Exception {
        FormImage image = service.upload("form-id", 3L, new MockMultipartFile("files", "../../camera.png", "application/octet-stream", png()));
        assertEquals("image/png", image.contentType()); assertEquals("camera.png", image.name());
        assertTrue(image.objectKey().startsWith("formularios/form-id/perguntas/3/"));
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("test-form-images", request.getValue().bucket());
        assertEquals("image/png", request.getValue().contentType());
    }

    @Test void cleansUpAnUploadWhoseOutcomeIsUncertain() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class, () -> service.upload("form", 1L,
                new MockMultipartFile("files", "a.png", "image/png", png())));
        ArgumentCaptor<DeleteObjectRequest> deletion = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deletion.capture()); assertEquals("test-form-images", deletion.getValue().bucket());
    }

    @Test void signsPrivateUrlsLocallyWithExpirationCappedAtFifteenMinutes() {
        try (S3Presigner presigner = S3Presigner.builder().endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build()) {
            when(presigners.getObject()).thenReturn(presigner);
            FormImage image = new FormImage("image-id", "formularios/id/image.jpg", "camera.jpg", "image/jpeg", 10);
            PresignedDownload download = service.download(image);
            assertTrue(download.url().contains("X-Amz-Expires=900"));
            assertTrue(download.expiresAt().isBefore(Instant.now().plusSeconds(901)));
            verifyNoInteractions(client);
        }
    }
}

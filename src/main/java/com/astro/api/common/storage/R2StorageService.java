package com.astro.api.common.storage;

import com.astro.api.config.R2Properties;
import com.astro.api.common.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

@Service
public class R2StorageService {

    private static final int MAX_PHOTO_SIZE = 5 * 1024 * 1024;

    private final ObjectProvider<S3Presigner> presigner;
    private final R2Properties properties;
    private final ObjectProvider<S3Client> client;

    public R2StorageService(ObjectProvider<S3Presigner> presigner, R2Properties properties,
                            ObjectProvider<S3Client> client) {
        this.presigner = presigner;
        this.properties = properties;
        this.client = client;
    }

    public String uploadProfilePhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("O arquivo da foto é obrigatório.");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new BusinessException("A foto deve ter no máximo 5 MB.");
        }
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_PHOTO_SIZE + 1);
        } catch (IOException exception) {
            throw new UncheckedIOException("Não foi possível ler a foto enviada.", exception);
        }
        if (bytes.length > MAX_PHOTO_SIZE) {
            throw new BusinessException("A foto deve ter no máximo 5 MB.");
        }
        String extension = imageExtension(bytes);
        String contentType = "image/" + (extension.equals("jpg") ? "jpeg" : extension);
        String objectPath = "usuarios/" + userId + "/" + UUID.randomUUID() + "." + extension;
        properties.validate();
        client.getObject().putObject(PutObjectRequest.builder().bucket(properties.bucket())
                        .key(objectPath).contentType(contentType).build(), RequestBody.fromBytes(bytes));
        return objectPath;
    }

    public void deleteObject(String objectPath) {
        properties.validate();
        client.getObject().deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket()).key(objectPath).build());
    }

    private String imageExtension(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13
                && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) {
            return "png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        throw new BusinessException("A foto deve estar no formato JPEG, PNG ou WebP.");
    }

    public String getDownloadUrl(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        properties.validate();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlDuration())
                .getObjectRequest(builder -> builder.bucket(properties.bucket()).key(objectPath))
                .build();
        return presigner.getObject().presignGetObject(request).url().toExternalForm();
    }
}

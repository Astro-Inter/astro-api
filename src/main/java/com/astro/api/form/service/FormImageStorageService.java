package com.astro.api.form.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.config.R2Properties;
import com.astro.api.form.dto.response.FormImageDownloadResponse;
import com.astro.api.form.model.FormImage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FormImageStorageService {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final long MAX_PIXELS = 32_000_000L;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectProvider<S3Presigner> s3Presigner;
    private final R2Properties r2Properties;
    private final String bucketName;

    public FormImageStorageService(ObjectProvider<S3Client> s3Client, ObjectProvider<S3Presigner> s3Presigner,
                                   R2Properties r2Properties, @Value("${storage.r2.form-images-bucket:}") String bucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.r2Properties = r2Properties;
        this.bucketName = bucketName;
    }

    public FormImage upload(String formId, Long questionId, MultipartFile file) {
        byte[] bytes = readImage(file);
        String extension = validateImage(bytes);
        requireConfiguration();
        String imageId = UUID.randomUUID().toString();
        String objectKey = "formularios/" + formId + "/perguntas/" + questionId + "/" + imageId + "." + extension;
        String contentType = extension.equals("jpg") ? "image/jpeg" : "image/png";
        FormImage image = new FormImage(imageId, objectKey, fileName(file.getOriginalFilename(), extension), contentType, bytes.length);
        try {
            s3Client.getObject().putObject(PutObjectRequest.builder().bucket(bucketName).key(objectKey)
                    .contentType(contentType).build(), RequestBody.fromBytes(bytes));
        } catch (RuntimeException failure) {
            // A timeout can occur after R2 accepted the object; remove only this request's generated key.
            try { delete(image); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
        return image;
    }

    private byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BusinessException("Cada imagem deve ter entre 1 byte e 50 MB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new BusinessException("Imagem vazia ou acima de 50 MB");
            return bytes;
        } catch (IOException exception) {
            throw new BusinessException("Não foi possível ler a imagem enviada");
        }
    }

    String validateImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("Envie uma imagem JPEG ou PNG válida");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format)) throw new BusinessException("Envie uma imagem JPEG ou PNG válida");
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > MAX_PIXELS) {
                    throw new BusinessException("A imagem deve ter no máximo 32 milhões de pixels");
                }
                if (reader.read(0) == null) throw new BusinessException("Imagem inválida");
                return format.equals("png") ? "png" : "jpg";
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException("Envie uma imagem JPEG ou PNG válida");
        }
    }

    private String fileName(String originalName, String extension) {
        String name = originalName == null ? "imagem." + extension : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        return name.isBlank() ? "imagem." + extension : name.substring(0, Math.min(name.length(), 200));
    }

    public void delete(FormImage image) {
        requireConfiguration();
        s3Client.getObject().deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(image.objectKey()).build());
    }

    public FormImageDownloadResponse download(FormImage image) {
        requireConfiguration();
        Duration duration = r2Properties.presignedUrlDuration().compareTo(Duration.ofMinutes(15)) > 0
                ? Duration.ofMinutes(15) : r2Properties.presignedUrlDuration();
        PresignedGetObjectRequest signedRequest = s3Presigner.getObject().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(duration.truncatedTo(ChronoUnit.SECONDS))
                .getObjectRequest(request -> request.bucket(bucketName).key(image.objectKey())
                        .responseContentType(image.contentType()).responseContentDisposition("inline"))
                .build());
        return new FormImageDownloadResponse(image.id(), signedRequest.url().toExternalForm(),
                signedRequest.expiration().truncatedTo(ChronoUnit.SECONDS));
    }

    private void requireConfiguration() {
        r2Properties.validateConnection();
        if (bucketName == null || bucketName.isBlank()) throw new IllegalStateException("Configure R2_BUCKET_FORM_IMAGES");
    }
}

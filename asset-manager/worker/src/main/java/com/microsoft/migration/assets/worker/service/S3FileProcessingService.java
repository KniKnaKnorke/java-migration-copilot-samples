package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.blob.container-name}")
    private String containerName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        try (InputStream inputStream = blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .openInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .uploadFromFile(source.toString(), true);

        // Extract the original key from the thumbnail key
        String originalKey = extractOriginalKey(key);

        // Find and update metadata
        imageMetadataRepository.findAll().stream()
            .filter(metadata -> metadata.getS3Key().equals(originalKey))
            .findFirst()
            .ifPresent(metadata -> {
                metadata.setThumbnailKey(key);
                metadata.setThumbnailUrl(generateUrl(key));
                imageMetadataRepository.save(metadata);
            });
    }

    @Override
    public String getStorageType() {
        return "azure";
    }

    @Override
    protected String generateUrl(String key) {
        return blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .getBlobUrl();
    }

    private String extractOriginalKey(String key) {
        // For a key like "xxxxx_thumbnail.png", get "xxxxx.png"
        String suffix = "_thumbnail";
        int extensionIndex = key.lastIndexOf('.');
        if (extensionIndex > 0) {
            String nameWithoutExtension = key.substring(0, extensionIndex);
            String extension = key.substring(extensionIndex);

            int suffixIndex = nameWithoutExtension.lastIndexOf(suffix);
            if (suffixIndex > 0) {
                return nameWithoutExtension.substring(0, suffixIndex) + extension;
            }
        }
        return key;
    }
}

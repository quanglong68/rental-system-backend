package com.rental.propertiesservice.features.imagestorage.infrastructure;

public record StorageObject(
        String key,
        String url,
        String contentType,
        long size
) {
}

package com.rental.propertiesservice.features.imagestorage.infrastructure;

public interface ObjectStorage {
    StorageObject upload(String key, byte[] data, String contentType, long contentLength);
    void delete(String key);
}

package com.ps.weatherapp.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Component
public class CacheManager<T> {
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    public CacheManager() {
        this.objectMapper = new ObjectMapper();
    }

    // Serialize the cache to a JSON string
    public String serializeCache(Map<String, T> cache) {
        try {
            return objectMapper.writeValueAsString(cache);
        } catch (IOException e) {
            logger.error("Error serializing cache: {}", e.getMessage());
            return null;
        }
    }

    // Deserialize JSON string to the cache structure
    public Map<String, T> deserializeCache(String json, TypeReference<Map<String, T>> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException e) {
            logger.error("Error deserializing cache: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // Save the cache to a file
    public void saveCacheToFile(String fileName, Map<String, T> cache) {
        try {
            String json = serializeCache(cache);
            if (json != null) {
                File file = new File(fileName);
                if (!file.exists()) {
                    file.createNewFile();
                }
                Files.write(Paths.get(fileName), json.getBytes());
            }
        } catch (IOException e) {
            logger.error("Error saving cache to file: {}", e.getMessage());
        }
    }

    // Load the cache from a file
    public Map<String, T> loadCacheFromFile(String fileName, TypeReference<Map<String, T>> typeReference) {
        try {
            File file = new File(fileName);
            if (file.exists()) {
                String json = new String(Files.readAllBytes(Paths.get(fileName)));
                return deserializeCache(json, typeReference);
            } else {
                logger.info("Cache file not found. Creating a new empty cache.");
                return new HashMap<>();
            }
        } catch (IOException e) {
            logger.warn("Error loading cache from file: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}


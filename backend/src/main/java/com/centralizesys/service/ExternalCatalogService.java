package com.centralizesys.service;

import com.centralizesys.model.dto.external.CatalogProductDto;
import com.centralizesys.repository.ExternalCatalogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ExternalCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ExternalCatalogService.class);
    private static final String CATALOG_CACHE_KEY = "ACTIVE_CATALOG";

    private final ExternalCatalogRepository externalCatalogRepository;
    private final ObjectMapper objectMapper;

    /**
     * In-memory cache holding the aggregated catalog and its precomputed ETag.
     * Atomic get() guarantees a single database query even under high concurrent load.
     */
    private final Cache<String, CachedCatalogResponse> catalogCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(5)
            .build();

    public ExternalCatalogService(ExternalCatalogRepository externalCatalogRepository, ObjectMapper objectMapper) {
        this.externalCatalogRepository = externalCatalogRepository;
        this.objectMapper = objectMapper;
    }

    public CachedCatalogResponse getCatalog() {
        return catalogCache.get(CATALOG_CACHE_KEY, k -> loadAndBuildCatalogResponse());
    }

    public void evictCatalogCache() {
        catalogCache.invalidate(CATALOG_CACHE_KEY);
        log.debug("Evicted external catalog cache.");
    }

    private CachedCatalogResponse loadAndBuildCatalogResponse() {
        log.debug("Cache miss: loading external catalog from database...");
        List<CatalogProductDto> items = externalCatalogRepository.fetchActiveCatalog();
        String eTag = computeETag(items);
        return new CachedCatalogResponse(items, eTag);
    }

    private String computeETag(List<CatalogProductDto> items) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(items);
            return "\"" + hashSha256(jsonBytes) + "\"";
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize catalog to JSON for ETag, falling back to item hashCode: {}", e.getMessage());
            String fallback = String.valueOf(items.hashCode());
            return "\"" + hashSha256(fallback.getBytes(StandardCharsets.UTF_8)) + "\"";
        }
    }

    private String hashSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available in JVM", e);
        }
    }

    public record CachedCatalogResponse(
            List<CatalogProductDto> items,
            String eTag
    ) {}
}

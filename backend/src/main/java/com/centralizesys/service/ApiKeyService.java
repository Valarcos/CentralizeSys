package com.centralizesys.service;

import com.centralizesys.model.auth.ApiKey;
import com.centralizesys.repository.ApiKeyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final ZoneId ZONE_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String KEY_PREFIX = "cs_live_";
    private static final int RANDOM_BYTE_COUNT = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * In-memory cache for fast-path API Key verification.
     * Maps SHA-256 hash -> ApiKey entity.
     */
    private final Cache<String, ApiKey> keyCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Generates a new cryptographically secure API key.
     * Persists ONLY the SHA-256 hash. Returns the raw plaintext token once.
     */
    public GeneratedApiKeyResponse generateKey(String nombre, int rateLimitRpm) {
        byte[] randomBytes = new byte[RANDOM_BYTE_COUNT];
        secureRandom.nextBytes(randomBytes);
        String rawToken = KEY_PREFIX + HexFormat.of().formatHex(randomBytes);

        String keyHash = hashKey(rawToken);
        LocalDateTime now = LocalDateTime.now(ZONE_AR);

        ApiKey apiKey = ApiKey.builder()
                .nombre(nombre.trim())
                .keyHash(keyHash)
                .activo(true)
                .creadoEn(now)
                .ultimoAcceso(null)
                .rateLimitRpm(rateLimitRpm > 0 ? rateLimitRpm : 10)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        keyCache.put(keyHash, saved);

        log.info("Generated new API Key id={} name='{}'", saved.getId(), saved.getNombre());
        return new GeneratedApiKeyResponse(saved.getId(), saved.getNombre(), rawToken, saved.getRateLimitRpm(), saved.getCreadoEn());
    }

    /**
     * Validates an incoming raw API key against stored cryptographic hashes using constant-time comparison.
     */
    public Optional<ApiKey> validateKey(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }

        String computedHash = hashKey(rawToken);
        ApiKey apiKey = keyCache.get(computedHash, h -> apiKeyRepository.findByHash(h).orElse(null));

        if (apiKey == null || !apiKey.isActivo()) {
            return Optional.empty();
        }

        // Constant-time comparison to prevent timing side-channel attacks
        boolean matches = MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                apiKey.getKeyHash().getBytes(StandardCharsets.UTF_8)
        );

        return matches ? Optional.of(apiKey) : Optional.empty();
    }

    /**
     * Throttled timestamp update: only writes to the DB if the existing timestamp is > 5 minutes old.
     */
    public void recordAccessThrottled(Long id) {
        try {
            LocalDateTime now = LocalDateTime.now(ZONE_AR);
            LocalDateTime threshold = now.minusMinutes(5);
            apiKeyRepository.updateUltimoAccesoThrottled(id, now, threshold);
        } catch (Exception e) {
            log.warn("Failed to update ultimo_acceso for API Key id={}: {}", id, e.getMessage());
        }
    }

    /**
     * Revokes an active API key immediately and evicts it from the cache.
     */
    public void revokeKey(Long id) {
        apiKeyRepository.revoke(id);
        keyCache.invalidateAll();
        log.info("Revoked API Key id={}", id);
    }

    public List<ApiKey> findAll() {
        return apiKeyRepository.findAll();
    }

    public String hashKey(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public record GeneratedApiKeyResponse(
            Long id,
            String nombre,
            String rawToken,
            int rateLimitRpm,
            LocalDateTime creadoEn
    ) {}
}

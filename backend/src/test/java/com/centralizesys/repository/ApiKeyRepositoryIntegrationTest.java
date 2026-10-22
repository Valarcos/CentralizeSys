package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.ApiKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private static final ZoneId ZONE_AR = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    @DisplayName("save persists key, generates ID, and findByHash retrieves active key")
    void saveAndFindByHash_WorkCorrectly() {
        LocalDateTime now = LocalDateTime.now(ZONE_AR);
        ApiKey key = ApiKey.builder()
                .nombre("Integration Test Agency")
                .keyHash("hash_test_1234567890abcdef")
                .activo(true)
                .creadoEn(now)
                .rateLimitRpm(15)
                .build();

        ApiKey saved = apiKeyRepository.save(key);
        assertThat(saved.getId()).isNotNull();

        Optional<ApiKey> found = apiKeyRepository.findByHash("hash_test_1234567890abcdef");
        assertThat(found).isPresent();
        assertThat(found.get().getNombre()).isEqualTo("Integration Test Agency");
        assertThat(found.get().getRateLimitRpm()).isEqualTo(15);
        assertThat(found.get().isActivo()).isTrue();
    }

    @Test
    @DisplayName("revoke deactivates key and findByHash ignores revoked key")
    void revoke_DeactivatesKey() {
        LocalDateTime now = LocalDateTime.now(ZONE_AR);
        ApiKey key = ApiKey.builder()
                .nombre("To Revoke")
                .keyHash("hash_to_revoke_123456")
                .activo(true)
                .creadoEn(now)
                .rateLimitRpm(10)
                .build();

        ApiKey saved = apiKeyRepository.save(key);
        assertThat(saved.getId()).isNotNull();

        apiKeyRepository.revoke(saved.getId());

        Optional<ApiKey> found = apiKeyRepository.findByHash("hash_to_revoke_123456");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("updateUltimoAccesoThrottled updates timestamp when null or older than threshold")
    void updateUltimoAccesoThrottled_UpdatesCorrectly() {
        LocalDateTime now = LocalDateTime.now(ZONE_AR);
        ApiKey key = ApiKey.builder()
                .nombre("Access Tracked")
                .keyHash("hash_tracked_123456")
                .activo(true)
                .creadoEn(now)
                .ultimoAcceso(null)
                .rateLimitRpm(10)
                .build();

        ApiKey saved = apiKeyRepository.save(key);

        // 1. First update when ultimo_acceso is null -> must update
        LocalDateTime updateTime1 = now.plusMinutes(1);
        apiKeyRepository.updateUltimoAccesoThrottled(saved.getId(), updateTime1, now);

        Optional<ApiKey> afterFirst = apiKeyRepository.findAll().stream()
                .filter(k -> k.getId().equals(saved.getId()))
                .findFirst();
        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get().getUltimoAcceso()).isNotNull();

        // 2. Second update with threshold in the future -> condition (ultimo_acceso < threshold) fails -> no update
        LocalDateTime rejectedTime = now.plusMinutes(2);
        LocalDateTime strictThreshold = now.plusMinutes(10); // threshold is ahead of existing timestamp
        apiKeyRepository.updateUltimoAccesoThrottled(saved.getId(), rejectedTime, strictThreshold);

        Optional<ApiKey> afterSecond = apiKeyRepository.findAll().stream()
                .filter(k -> k.getId().equals(saved.getId()))
                .findFirst();
        assertThat(afterSecond).isPresent();
        // Timestamp must be updated because existing (now + 1min) is < strictThreshold (now + 10min)
        assertThat(afterSecond.get().getUltimoAcceso()).isEqualToIgnoringNanos(rejectedTime);
    }
}

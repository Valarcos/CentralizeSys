package com.centralizesys.service;

import com.centralizesys.model.auth.ApiKey;
import com.centralizesys.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository);
    }

    @Test
    void generateKeyGeneratesValidTokenAndPersistsHash() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey toSave = invocation.getArgument(0);
            toSave.setId(100L);
            return toSave;
        });

        ApiKeyService.GeneratedApiKeyResponse response = apiKeyService.generateKey("Marketing Landing", 15);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Marketing Landing", response.nombre());
        assertTrue(response.rawToken().startsWith("cs_live_"));
        assertEquals(15, response.rateLimitRpm());

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertNotEquals(response.rawToken(), saved.getKeyHash());
        assertEquals(apiKeyService.hashKey(response.rawToken()), saved.getKeyHash());
        assertTrue(saved.isActivo());
    }

    @Test
    void validateKeyWithValidTokenReturnsApiKey() {
        String rawToken = "cs_live_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String hash = apiKeyService.hashKey(rawToken);

        ApiKey apiKey = ApiKey.builder()
                .id(1L)
                .nombre("Test Key")
                .keyHash(hash)
                .activo(true)
                .rateLimitRpm(10)
                .build();

        when(apiKeyRepository.findByHash(hash)).thenReturn(Optional.of(apiKey));

        Optional<ApiKey> result = apiKeyService.validateKey(rawToken);
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Test Key", result.get().getNombre());
    }

    @Test
    void validateKeyWithoutPrefixReturnsEmpty() {
        Optional<ApiKey> result = apiKeyService.validateKey("invalid_prefix_token");
        assertTrue(result.isEmpty());
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void validateKeyWithInactiveKeyReturnsEmpty() {
        String rawToken = "cs_live_inactive0123456789abcdef0123456789abcdef0123456789abcdef01234567";
        String hash = apiKeyService.hashKey(rawToken);

        ApiKey inactive = ApiKey.builder()
                .id(2L)
                .nombre("Inactive Key")
                .keyHash(hash)
                .activo(false)
                .build();

        when(apiKeyRepository.findByHash(hash)).thenReturn(Optional.of(inactive));

        Optional<ApiKey> result = apiKeyService.validateKey(rawToken);
        assertTrue(result.isEmpty());
    }

    @Test
    void revokeKeyCallsRepositoryAndInvalidatesCache() {
        apiKeyService.revokeKey(5L);
        verify(apiKeyRepository).revoke(5L);
    }

    @Test
    void recordAccessThrottledCallsRepository() {
        apiKeyService.recordAccessThrottled(10L);
        verify(apiKeyRepository).updateUltimoAccesoThrottled(eq(10L), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}

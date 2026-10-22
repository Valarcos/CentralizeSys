package com.centralizesys.controller;

import com.centralizesys.service.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Administrative controller for generating, inspecting, and revoking M2M API Keys.
 * Restricted strictly to users holding {@code ROLE_ADMIN} or {@code ROLE_OWNER}.
 */
@RestController
@RequestMapping("/api/admin/api-keys")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyService.GeneratedApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {
        int rpm = request.rateLimitRpm() != null && request.rateLimitRpm() > 0 ? request.rateLimitRpm() : 10;
        ApiKeyService.GeneratedApiKeyResponse response = apiKeyService.generateKey(request.nombre(), rpm);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeySummaryResponse>> listApiKeys() {
        List<ApiKeySummaryResponse> list = apiKeyService.findAll().stream()
                .map(k -> new ApiKeySummaryResponse(
                        k.getId(),
                        k.getNombre(),
                        k.isActivo(),
                        k.getRateLimitRpm(),
                        k.getCreadoEn(),
                        k.getUltimoAcceso()
                ))
                .toList();
        return ResponseEntity.ok(list);
    }

    @RequestMapping(value = "/{id}/revoke", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long id) {
        apiKeyService.revokeKey(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateApiKeyRequest(
            @NotBlank(message = "El nombre de la clave es obligatorio") String nombre,
            Integer rateLimitRpm
    ) {}

    public record ApiKeySummaryResponse(
            Long id,
            String nombre,
            boolean activo,
            int rateLimitRpm,
            LocalDateTime creadoEn,
            LocalDateTime ultimoAcceso
    ) {}
}

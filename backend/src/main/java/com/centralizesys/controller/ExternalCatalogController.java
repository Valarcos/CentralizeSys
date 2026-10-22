package com.centralizesys.controller;

import com.centralizesys.model.dto.external.CatalogProductDto;
import com.centralizesys.service.ExternalCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Public/B2B Integration Controller exposing active products and availability for external landing pages.
 *
 * <p>Protected exclusively by {@code ApiKeyAuthenticationFilter} requiring {@code ROLE_EXTERNAL_SERVICE}.
 * Supports conditional HTTP requests (ETag / If-None-Match) returning 304 Not Modified with zero body payload.
 */
@RestController
@RequestMapping("/api/external/catalogo")
public class ExternalCatalogController {

    private final ExternalCatalogService externalCatalogService;

    public ExternalCatalogController(ExternalCatalogService externalCatalogService) {
        this.externalCatalogService = externalCatalogService;
    }

    @GetMapping
    public ResponseEntity<List<CatalogProductDto>> getActiveCatalog(HttpServletRequest request) {
        ExternalCatalogService.CachedCatalogResponse cached = externalCatalogService.getCatalog();

        String ifNoneMatch = request.getHeader("If-None-Match");
        CacheControl cacheControl = CacheControl.maxAge(15, TimeUnit.MINUTES)
                .cachePublic()
                .mustRevalidate();

        if (ifNoneMatch != null && ifNoneMatch.equals(cached.eTag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(cached.eTag())
                    .cacheControl(cacheControl)
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(cached.eTag())
                .cacheControl(cacheControl)
                .body(cached.items());
    }
}

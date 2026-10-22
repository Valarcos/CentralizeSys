package com.centralizesys.service;

import com.centralizesys.model.dto.external.CatalogProductDto;
import com.centralizesys.repository.ExternalCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalCatalogServiceTest {

    @Mock
    private ExternalCatalogRepository externalCatalogRepository;

    private ExternalCatalogService externalCatalogService;

    @BeforeEach
    void setUp() {
        externalCatalogService = new ExternalCatalogService(externalCatalogRepository, new ObjectMapper());
    }

    @Test
    void getCatalogLoadsFromRepositoryAndComputesETag() {
        List<CatalogProductDto> products = List.of(
                new CatalogProductDto("ART-1", "Producto 1", true),
                new CatalogProductDto("ART-2", "Producto 2", false)
        );
        when(externalCatalogRepository.fetchActiveCatalog()).thenReturn(products);

        ExternalCatalogService.CachedCatalogResponse response = externalCatalogService.getCatalog();

        assertNotNull(response);
        assertEquals(2, response.items().size());
        assertTrue(response.items().get(0).disponible());
        assertFalse(response.items().get(1).disponible());
        assertNotNull(response.eTag());
        assertTrue(response.eTag().startsWith("\"") && response.eTag().endsWith("\""));

        verify(externalCatalogRepository, times(1)).fetchActiveCatalog();
    }

    @Test
    void getCatalogCachesResponseInCaffeine() {
        List<CatalogProductDto> products = List.of(
                new CatalogProductDto("ART-1", "Producto 1", true)
        );
        when(externalCatalogRepository.fetchActiveCatalog()).thenReturn(products);

        ExternalCatalogService.CachedCatalogResponse response1 = externalCatalogService.getCatalog();
        ExternalCatalogService.CachedCatalogResponse response2 = externalCatalogService.getCatalog();

        assertSame(response1, response2);
        verify(externalCatalogRepository, times(1)).fetchActiveCatalog();
    }

    @Test
    void evictCatalogCacheForcesReloadOnNextRequest() {
        List<CatalogProductDto> products = List.of(
                new CatalogProductDto("ART-1", "Producto 1", true)
        );
        when(externalCatalogRepository.fetchActiveCatalog()).thenReturn(products);

        externalCatalogService.getCatalog();
        externalCatalogService.evictCatalogCache();
        externalCatalogService.getCatalog();

        verify(externalCatalogRepository, times(2)).fetchActiveCatalog();
    }
}

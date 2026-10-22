package com.centralizesys.controller;

import com.centralizesys.model.dto.external.CatalogProductDto;
import com.centralizesys.service.ExternalCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExternalCatalogControllerTest {

    @Mock
    private ExternalCatalogService externalCatalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExternalCatalogController(externalCatalogService)).build();
    }

    @Test
    void getActiveCatalogReturnsOkWithPayloadAndCachingHeaders() throws Exception {
        String testEtag = "\"abc123etag\"";
        List<CatalogProductDto> items = List.of(
                new CatalogProductDto("ART-100", "Remera Algodon", true),
                new CatalogProductDto("ART-200", "Pantalon Jean", false)
        );
        ExternalCatalogService.CachedCatalogResponse response =
                new ExternalCatalogService.CachedCatalogResponse(items, testEtag);

        when(externalCatalogService.getCatalog()).thenReturn(response);

        mockMvc.perform(get("/api/external/catalogo"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", testEtag))
                .andExpect(header().string("Cache-Control", "max-age=900, must-revalidate, public"))
                .andExpect(jsonPath("$[0].codigo").value("ART-100"))
                .andExpect(jsonPath("$[0].descripcion").value("Remera Algodon"))
                .andExpect(jsonPath("$[0].disponible").value(true))
                .andExpect(jsonPath("$[1].codigo").value("ART-200"))
                .andExpect(jsonPath("$[1].disponible").value(false))
                .andExpect(jsonPath("$[0].stock").doesNotExist()); // Verification: raw stock is NOT exposed
    }

    @Test
    void getActiveCatalogWithMatchingIfNoneMatchReturns304NotModified() throws Exception {
        String testEtag = "\"abc123etag\"";
        List<CatalogProductDto> items = List.of(
                new CatalogProductDto("ART-100", "Remera Algodon", true)
        );
        ExternalCatalogService.CachedCatalogResponse response =
                new ExternalCatalogService.CachedCatalogResponse(items, testEtag);

        when(externalCatalogService.getCatalog()).thenReturn(response);

        mockMvc.perform(get("/api/external/catalogo").header("If-None-Match", testEtag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", testEtag))
                .andExpect(header().string("Cache-Control", "max-age=900, must-revalidate, public"))
                .andExpect(content().string("")); // Zero-byte payload body
    }
}

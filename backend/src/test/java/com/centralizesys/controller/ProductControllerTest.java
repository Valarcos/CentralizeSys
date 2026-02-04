package com.centralizesys.controller;

import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.ProductRequest;
import com.centralizesys.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("Employee can create product")
    @WithMockUser(username = "owner", roles = { "EMPLEADO" })
    void createProduct_AsEmployee_Success() throws Exception {
        ProductRequest p = new ProductRequest();
        p.setCodigo("A-100");
        p.setDescripcion("Test");
        p.setPrecioMinorista(100.0);
        p.setPrecioCosto(50.0);

        when(productService.create(any(Product.class)))
                .thenReturn(new Product(1L, "A-100", "Test", 50.0, null, 100.0, 0L));

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isCreated());

        verify(productService).create(any(Product.class)); // Create does not take User ID currently
    }

    @Test
    @DisplayName("Employee can delete product")
    @WithMockUser(username = "owner", roles = { "EMPLEADO" })
    void deleteProduct_AsEmployee_Success() throws Exception {
        mockMvc.perform(delete("/api/productos/123"))
                .andExpect(status().isNoContent());

        verify(productService).deleteById(eq(123L), any());
    }

    @Test
    @DisplayName("Unauthenticated cannot create product")
    void createProduct_Unauthenticated_Forbidden() throws Exception {
        Product p = new Product();
        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Employee can update product")
    @WithMockUser(username = "owner", roles = { "EMPLEADO" })
    void updateProduct_AsEmployee_Success() throws Exception {
        ProductRequest p = new ProductRequest();
        p.setCodigo("A-100");
        p.setDescripcion("Updated Desc");
        p.setPrecioMinorista(200.0);
        p.setPrecioCosto(100.0);

        mockMvc.perform(put("/api/productos/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isNoContent());

        verify(productService).update(eq(100L), any(Product.class));
    }

    @Test
    @DisplayName("Create Product with Invalid Data returns 400")
    @WithMockUser(username = "owner", roles = { "EMPLEADO" })
    void createProduct_InvalidData_Returns400() throws Exception {
        ProductRequest p = new ProductRequest();
        // Missing Description, Prices, etc.

        // Mock the Service validation logic (since Mock doesn't run real code)
        when(productService.create(any(Product.class)))
                .thenThrow(new com.centralizesys.exception.BusinessRuleException("Invalid Data"));

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Delete Non-Existent Product returns 404")
    @WithMockUser(username = "owner", roles = { "EMPLEADO" })
    void deleteProduct_NotFound_Returns404() throws Exception {
        doThrow(new com.centralizesys.exception.ResourceNotFoundException("Product", 999L))
                .when(productService).deleteById(eq(999L), any());

        mockMvc.perform(delete("/api/productos/999"))
                .andExpect(status().isNotFound());
    }
}

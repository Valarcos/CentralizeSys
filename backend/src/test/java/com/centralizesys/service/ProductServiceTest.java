package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private StockService stockService; // [NEW MOCK]

    @org.mockito.InjectMocks
    private ProductService service;

    // --- Read / Pass-through Tests ---

    @Test
    @DisplayName("getAll returns list from repository")
    void getAll_ReturnsList() {
        Product p = new Product(1L, "C", "D", 1.0, 1.0, 1.0, 0L);
        when(repository.findAll()).thenReturn(List.of(p));
        List<Product> result = service.getAll();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAllOrSearch (Browse) returns PageResponse")
    void getAllOrSearch_Browse() {
        Product p = new Product(1L, "C", "D", 1.0, 1.0, 1.0, 0L);
        when(repository.findAll(20L, 0L)).thenReturn(List.of(p));
        when(repository.countAll()).thenReturn(1L);

        PageResponse<Product> result = service.getAllOrSearch(null, 0L, 20L);

        assertEquals(1, result.content().size());
        assertEquals(1L, result.totalElements());
    }

    @Test
    @DisplayName("getAllOrSearch (Search) returns PageResponse")
    void getAllOrSearch_Search() {
        Product p = new Product(1L, "C", "D", 1.0, 1.0, 1.0, 0L);
        when(repository.search("query")).thenReturn(List.of(p));

        PageResponse<Product> result = service.getAllOrSearch("query", 0L, 20L);

        assertEquals(1, result.content().size());
        assertEquals(100L, result.size()); // Search creates fixed size 100 PageResponse
        assertEquals(0L, result.page());
    }

    @Test
    @DisplayName("getById returns product when found")
    void getById_Success() {
        Product p = new Product(1L, "C", "D", 1.0, 1.0, 1.0, 0L);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Product result = service.getById(1L);
        assertNotNull(result);
        assertEquals("C", result.getCodigo());
    }

    @Test
    @DisplayName("getById throws when not found")
    void getById_NotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("getVariantsByCode delegates to repository")
    void getVariantsByCode_Delegates() {
        when(repository.findAllByCodigo("ABC")).thenReturn(Collections.emptyList());
        assertTrue(service.getVariantsByCode("ABC").isEmpty());
        verify(repository).findAllByCodigo("ABC");
    }

    @Test
    @DisplayName("search delegates to repository")
    void search_Delegates() {
        when(repository.search("query")).thenReturn(Collections.emptyList());
        assertTrue(service.search("query").isEmpty());
        verify(repository).search("query");
    }

    @Test
    @DisplayName("Validate throws for null code")
    void validate_NullCode() {
        Product p = new Product(null, "Desc", 10.0, 10.0, 10.0);
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for blank description")
    void validate_BlankDescription() {
        Product p = new Product("A", "", 10.0, 10.0, 10.0);
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for negative retail price")
    void validate_NegativeRetail() {
        Product p = new Product("A", "Desc", 10.0, 10.0, -1.0);
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for negative cost")
    void validate_NegativeCost() {
        Product p = new Product("A", "Desc", -1.0, 10.0, 10.0);
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    // --- CompareDouble Tests ---

    @Test
    @DisplayName("CompareDouble handles nulls gracefully")
    void compareDouble_Nulls() {
        assertFalse(service.compareDouble(null, 10.0));
        assertFalse(service.compareDouble(10.0, null));
        assertFalse(service.compareDouble(null, null));
    }

    @Test
    @DisplayName("CompareDouble detects equality within epsilon")
    void compareDouble_Equality() {
        assertTrue(service.compareDouble(10.0, 10.0));
        assertTrue(service.compareDouble(10.0, 10.0000001));
    }

    @Test
    @DisplayName("CompareDouble detects inequality")
    void compareDouble_Inequality() {
        assertFalse(service.compareDouble(10.0, 10.01));
    }

    // --- Create Tests ---

    @Test
    @DisplayName("Create saves valid product")
    void create_Success() {
        Product p = new Product("CODE", "Desc", 10.0, 10.0, 20.0);
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(p);

        Product created = service.create(p);
        assertNotNull(created);
        verify(repository).save(p);
    }

    @Test
    @DisplayName("createWithStock saves product and calls stock service")
    void createWithStock_Success() {
        Product p = new Product("CODE", "Desc", 10.0, 10.0, 20.0);
        Product saved = new Product(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L);

        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(saved);
        when(repository.findById(1L)).thenReturn(Optional.of(saved)); // For refresh call

        Product created = service.createWithStock(p, 5L, 10L);

        assertNotNull(created);
        verify(repository).save(p);
        verify(stockService).addStock(1L, 5L, 10L);
    }

    @Test
    @DisplayName("createWithStock does NOT call stock service if quantity is null/zero")
    void createWithStock_NoStock() {
        Product p = new Product("CODE", "Desc", 10.0, 10.0, 20.0);
        Product saved = new Product(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L);

        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(saved);

        service.createWithStock(p, 5L, null);

        verify(repository).save(p);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("Create throws on exact duplicate (Code+Cost+Price)")
    void create_Duplicate_Throws() {
        Product p = new Product("CODE", "Desc", 10.0, 10.0, 20.0);
        Product existing = new Product(1L, "CODE", "Old", 10.0, 10.0, 20.0, 0L);

        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(existing));

        assertThrows(BusinessRuleException.class, () -> service.create(p));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Create allows duplicate if code is '1' (Generic)")
    void create_Generic_AllowsDuplicates() {
        Product p = new Product("1", "Genérico", 10.0, 10.0, 20.0);

        // Even though existing matches perfectly, "1" bypasses the check purely in
        // logic
        when(repository.save(p)).thenReturn(p);

        assertDoesNotThrow(() -> service.create(p));
        verify(repository).save(p);
    }

    // --- Wholesale Price Default Tests (Issue #15) ---

    @Test
    @DisplayName("Create defaults null wholesale price to retail price")
    void create_NullWholesale_DefaultsToRetail() {
        Product p = new Product("CODE", "Desc", 10.0, null, 25.0);
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.create(p);

        assertNotNull(created.getPrecioMayorista(), "Wholesale price should not be null after create");
        assertEquals(25.0, created.getPrecioMayorista(), "Wholesale price should default to retail price");
    }

    @Test
    @DisplayName("CreateWithStock defaults null wholesale price to retail price")
    void createWithStock_NullWholesale_DefaultsToRetail() {
        Product p = new Product("CODE", "Desc", 10.0, null, 30.0);
        Product saved = new Product(1L, "CODE", "Desc", 10.0, 30.0, 30.0, 0L);

        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(any(Product.class))).thenReturn(saved);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));

        Product created = service.createWithStock(p, 5L, 10L);

        assertEquals(30.0, p.getPrecioMayorista(), "Original product object should have wholesale defaulted");
        assertNotNull(created);
    }

    @Test
    @DisplayName("Update defaults null wholesale price to retail price")
    void update_NullWholesale_DefaultsToRetail() {
        Product updateReq = new Product("CODE", "Desc", 10.0, null, 40.0);
        Product existing = new Product(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(existing));

        service.update(1L, updateReq);

        assertEquals(40.0, updateReq.getPrecioMayorista(), "Wholesale price should default to retail price on update");
        verify(repository).save(updateReq);
    }

    @Test
    @DisplayName("Create preserves explicit wholesale price (no default override)")
    void create_ExplicitWholesale_PreservedAsIs() {
        Product p = new Product("CODE", "Desc", 10.0, 15.0, 25.0);
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.create(p);

        assertEquals(15.0, created.getPrecioMayorista(), "Explicit wholesale price should NOT be overridden");
    }

    // --- Update Tests ---

    @Test
    @DisplayName("Update succeeds when no collision")
    void update_Success() {
        // Changing price from 20 to 25
        Product updateReq = new Product("CODE", "Desc", 10.0, 10.0, 25.0);
        Product existing = new Product(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(existing));
        // findAllByCodigo returns 'existing', but logic excludes 'self'

        service.update(1L, updateReq);

        verify(repository).save(updateReq);
        assertEquals(1L, updateReq.getId());
    }

    @Test
    @DisplayName("Update throws when colliding with ANOTHER product")
    void update_Collision_Throws() {
        // Trying to change Price to 30. But there is ANOTHER product (ID 2) with Cost
        // 10, Price 30.
        Product updateReq = new Product("CODE", "Desc", 10.0, 10.0, 30.0);
        Product existing = new Product(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L);
        Product other = new Product(2L, "CODE", "Other", 10.0, 10.0, 30.0, 0L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(existing, other));

        assertThrows(BusinessRuleException.class, () -> service.update(1L, updateReq));
        verify(repository, never()).save(updateReq);
    }

    // --- Delete Tests ---

    @Test
    @DisplayName("Delete calls audit service")
    void delete_Audits() {
        Product existing = new Product(1L, "C", "D", 1.0, 1.0, 1.0, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteById(1L, 999L);

        verify(repository).deleteById(1L);
        verify(auditoriaService).registrarAccion(eq(999L), eq("DELETE_PRODUCT"), contains("D"));
    }

    @Test
    @DisplayName("Delete throws if product not found")
    void delete_NotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteById(99L, 1L));
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Update throws if product not found")
    void update_NotFound() {
        Product p = new Product("C", "D", 1.0, 1.0, 1.0);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, p));
    }
}

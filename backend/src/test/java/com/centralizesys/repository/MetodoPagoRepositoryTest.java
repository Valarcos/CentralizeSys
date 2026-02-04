package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.MetodoPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetodoPagoRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private MetodoPagoRepository metodoPagoRepository;

    @Test
    @DisplayName("findAll - returns payment methods from seeded data")
    void findAll_returnsSeededPaymentMethods() {
        // Act
        List<MetodoPago> methods = metodoPagoRepository.findAll();

        // Assert - schema.sql seeds Efectivo, Debito, Credito, Transferencia
        assertThat(methods).isNotEmpty();
    }

    @Test
    @DisplayName("findAll - correctly maps all fields via RowMapper")
    void findAll_mapsAllFieldsCorrectly() {
        // Act
        List<MetodoPago> methods = metodoPagoRepository.findAll();

        // Assert - All fields should be non-null
        assertThat(methods).isNotEmpty().allSatisfy(m -> {
            assertThat(m.getId()).isNotNull();
            assertThat(m.getAcronimo()).isNotBlank();
            assertThat(m.getDescripcion()).isNotBlank();
        });
    }

    @Test
    @DisplayName("findAll - returns methods ordered by ID")
    void findAll_returnsOrderedById() {
        // Act
        List<MetodoPago> methods = metodoPagoRepository.findAll();

        // Assert - IDs should be in ascending order
        if (methods.size() > 1) {
            for (int i = 1; i < methods.size(); i++) {
                assertThat(methods.get(i).getId())
                        .isGreaterThan(methods.get(i - 1).getId());
            }
        }
    }
}

package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.audit.Auditoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditoriaRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Test
    @DisplayName("save - inserts audit log with all fields")
    void save_insertsAuditLog() {
        // Arrange
        Long userId = createTestUser();

        // Act
        auditoriaRepository.save(userId, "LOGIN", "User logged in successfully");

        // Assert
        List<Auditoria> logs = auditoriaRepository.findAll();
        assertThat(logs).isNotEmpty();
        assertThat(logs.getFirst().getUsuarioId()).isEqualTo(userId);
        assertThat(logs.getFirst().getAccion()).isEqualTo("LOGIN");
        assertThat(logs.getFirst().getDetalles()).isEqualTo("User logged in successfully");
    }

    @Test
    @DisplayName("save - allows null userId for system actions")
    void save_allowsNullUserId() {
        // Act - System action with null user (per FK constraint, 0 doesn't exist)
        auditoriaRepository.save(null, "BACKUP", "Automated backup completed");

        // Assert
        List<Auditoria> logs = auditoriaRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getUsuarioId()).isNull();
        assertThat(logs.getFirst().getAccion()).isEqualTo("BACKUP");
    }

    @Test
    @DisplayName("findByDateRange - returns logs within specified range")
    void findByDateRange_returnsLogsInRange() {
        // Arrange
        Long userId = createTestUser();

        // Insert logs first
        auditoriaRepository.save(userId, "ACTION_1", "First action");
        auditoriaRepository.save(userId, "ACTION_2", "Second action");

        // Use a range that covers "now" with buffer
        String startRange = LocalDateTime.now().minusHours(1).toString();
        String endRange = LocalDateTime.now().plusHours(1).toString();

        // Act
        List<Auditoria> results = auditoriaRepository.findByDateRange(startRange, endRange);

        // Assert
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findByDateRange - excludes logs outside range")
    void findByDateRange_excludesLogsOutsideRange() {
        // Arrange
        Long userId = createTestUser();
        auditoriaRepository.save(userId, "TEST_ACTION", "Action within range");

        // Query for a future range
        String futureStart = LocalDateTime.now().plusDays(1).toString();
        String futureEnd = LocalDateTime.now().plusDays(2).toString();

        // Act
        List<Auditoria> results = auditoriaRepository.findByDateRange(futureStart, futureEnd);

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findAll - returns logs ordered by fecha_hora DESC")
    void findAll_returnsOrderedDescending() {
        // Arrange
        Long userId = createTestUser();

        // Insert in order
        auditoriaRepository.save(userId, "FIRST", "First");
        // Small delay to ensure different timestamps
        auditoriaRepository.save(userId, "SECOND", "Second");
        auditoriaRepository.save(userId, "THIRD", "Third");

        // Act
        List<Auditoria> logs = auditoriaRepository.findAll();

        // Assert - Most recent (THIRD) should be first
        assertThat(logs).hasSize(3);
        assertThat(logs.getFirst().getAccion()).isEqualTo("THIRD");
        assertThat(logs.getLast().getAccion()).isEqualTo("FIRST");
    }
}

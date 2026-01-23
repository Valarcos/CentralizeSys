package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.product.Location;
import com.centralizesys.repository.StockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocationService {

    private final StockRepository repository;

    public LocationService(StockRepository repository) {
        this.repository = repository;
    }

    public List<Location> getAll() {
        return repository.findAllLocations();
    }

    public Location create(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("El identificador de la estantería no puede estar vacío.");
        }

        // Ensure it's a number (as per your requirement "only referred by their number")
        if (!name.matches("\\d+")) {
            throw new BusinessRuleException("El nombre de la estantería debe ser un número (ej: '1', '5').");
        }

        try {
            Long id = repository.createLocation(name);
            return new Location(id, name);
        } catch (DataIntegrityViolationException e) {
            // We catch the generic parent (in case Spring misses the mapping),
            // but INSPECT it to ensure we only report "Duplicate" if it's actually a Unique constraint.
            if (e.getMessage() != null && e.getMessage().toUpperCase().contains("UNIQUE")) {
                throw new BusinessRuleException("La estantería número '" + name + "' ya existe.");
            }
            // If it was another constraint (e.g. NOT NULL), rethrow it so we see the real 500 error.
            throw e;
        }
    }
}
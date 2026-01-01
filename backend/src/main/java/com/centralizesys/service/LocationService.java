package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.product.Location;
import com.centralizesys.repository.StockRepository;
import org.springframework.dao.DuplicateKeyException;
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
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("La estantería número '" + name + "' ya existe.");
        }
    }
}
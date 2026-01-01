package com.centralizesys.controller;

import com.centralizesys.model.product.Location;
import com.centralizesys.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    // GET /api/locations
    @GetMapping
    public ResponseEntity<List<Location>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // POST /api/locations
    // Accepts simple JSON: { "nombre": "5" }
    @PostMapping
    public ResponseEntity<Location> create(@RequestBody Map<String, String> request) {
        String nombre = request.get("nombre");
        Location created = service.create(nombre);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
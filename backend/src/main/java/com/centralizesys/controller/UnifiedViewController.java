package com.centralizesys.controller;

import com.centralizesys.service.UnifiedViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cobros-y-pedidos")
public class UnifiedViewController {

    private final UnifiedViewService unifiedViewService;

    public UnifiedViewController(UnifiedViewService unifiedViewService) {
        this.unifiedViewService = unifiedViewService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCobrosYPedidos() {
        return ResponseEntity.ok(unifiedViewService.getCobrosYPedidos());
    }
}

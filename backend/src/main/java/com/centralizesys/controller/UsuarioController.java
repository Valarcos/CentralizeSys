package com.centralizesys.controller;

import com.centralizesys.model.auth.LoginRequest;
import com.centralizesys.model.auth.RegisterRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioResponse;
import com.centralizesys.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
@SuppressWarnings("java:S5122") // Sonar: Explicitly ignoring CORS rule for local usage
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Endpoint for user login.
     * Returns User Details on success, or 400 Bad Request on failure.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request) {
        // Service throws BusinessRuleException if invalid, handled by GlobalExceptionHandler
        Usuario user = usuarioService.login(request.getEmail(), request.getPassword());

        // Map Entity -> Response DTO
        UsuarioResponse response = new UsuarioResponse(
                user.getId(),
                user.getNombre(),
                user.getEmail()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to register new admin users.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        usuarioService.registrarUsuario(
                request.getNombre(),
                request.getEmail(),
                request.getPassword()
        );
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
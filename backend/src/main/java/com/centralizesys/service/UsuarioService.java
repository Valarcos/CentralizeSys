package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Verifies credentials with EXPLICIT feedback for the elderly users.
     */
    public Usuario login(String email, String rawPassword) {
        // 1. Specific Check: Email Existence
        Usuario user = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("El correo electrónico no existe en el sistema."));

        // 2. Specific Check: Password Match
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("La contraseña es incorrecta.");
        }

        // After successful check:
        auditoriaService.registrarAccion(user.getId(), "LOGIN", "Inicio de sesión exitoso.");

        return user;
    }

    /**
     * Registers a new user.
     * Handles password hashing and email duplication checks.
     */
    @Transactional
    public void registrarUsuario(String nombre, String email, String rawPassword) {
        // 1. Check if email already exists
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("El correo '" + email + "' ya está registrado.");
        }

        // 2. Create Object & Hash Password
        Usuario newUser = new Usuario();
        newUser.setNombre(nombre);
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        newUser.setRol(UsuarioRole.EMPLEADO);

        // 3. Persist
        usuarioRepository.save(newUser);
    }
}
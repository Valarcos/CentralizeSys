package com.centralizesys.security;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.repository.UsuarioRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        // Defence-in-depth: the repository already filters activo=true, but we assert
        // the flag explicitly so this contract is clear and resilient to future refactors.
        if (!usuario.isActivo()) {
            throw new DisabledException("La cuenta del usuario está desactivada: " + email);
        }

        String roleName = "ROLE_" + usuario.getRol().name();

        return new CustomUserDetails(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getPasswordHash(),
                usuario.getNombre(),
                Collections.singletonList(new SimpleGrantedAuthority(roleName)));
    }
}

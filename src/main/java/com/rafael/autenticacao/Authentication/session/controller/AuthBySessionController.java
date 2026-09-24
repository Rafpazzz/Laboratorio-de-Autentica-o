package com.rafael.autenticacao.Authentication.session.controller;

import com.rafael.autenticacao.Authentication.shared.dto.LoginRequestDTO;
import com.rafael.autenticacao.Authentication.shared.dto.RegisterRequestDTO;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import com.rafael.autenticacao.Authentication.session.dto.SessionUserResponseDTO;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthBySessionController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UsuarioService usuarioService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthBySessionController(
            AuthenticationManager authenticationManager,
            @Qualifier("sessionSecurityContextRepository")
            SecurityContextRepository securityContextRepository,
            UsuarioService usuarioService,
            @Qualifier("sessionAuthenticationStrategy")
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            @Qualifier("sessionCsrfTokenRepository")
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.usuarioService = usuarioService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/session/login")
    public ResponseEntity<String> login(@RequestBody @Valid LoginRequestDTO requestDTO, HttpServletRequest request, HttpServletResponse response) {
        var token = new UsernamePasswordAuthenticationToken(requestDTO.email(), requestDTO.password());
        var authentication = authenticationManager.authenticate(token);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok("Usuario logado com sucesso");
    }

    @PostMapping("/session/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(null,request, response);

        var emptyContext = SecurityContextHolder.createEmptyContext();

        SecurityContextHolder.setContext(emptyContext);
        securityContextRepository.saveContext(emptyContext, request, response);

        return  ResponseEntity.ok("Logout feito");
    }

    @PostMapping("/session/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO requestDTO) {

        Entidade e = new Entidade(null ,requestDTO.nome(), requestDTO.email(), requestDTO.password(), requestDTO.age(), Role.USER);

        usuarioService.saveUser(e);

        return ResponseEntity.status(HttpStatus.CREATED).body("Usuario Criado");
    }

    @GetMapping("/session/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/session/sobre")
    public ResponseEntity<SessionUserResponseDTO> authenticationDetails(
            @AuthenticationPrincipal UsuarioDetails userDetails,
            Authentication authentication
    ) {
        List<String> authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();

        var response = new SessionUserResponseDTO(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getName(),
                userDetails.getEmail(),
                authorities
        );

        return ResponseEntity.ok(response);
    }

}

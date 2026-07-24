package com.rafael.autenticacao.Authentication.session.controller;

import com.rafael.autenticacao.Authentication.session.DTO.LoginRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthBySessionController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthBySessionController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/loginBySession")
    public ResponseEntity<String> login(@RequestBody LoginRequestDTO requestDTO, HttpServletRequest request, HttpServletResponse response) {
        var token = new UsernamePasswordAuthenticationToken(requestDTO.email(), requestDTO.password());
        var authentication = authenticationManager.authenticate(token);

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok("Usuario logado com sucesso");
    }

    @PostMapping("/logoutBySession")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();

        var session = request.getSession(false);

        if(session != null) {
            session.invalidate();
        }

        return  ResponseEntity.ok("Logout feito");
    }
}

package com.rafael.autenticacao.Usuario.Controller;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Dto.UsuarioPatchRequest;
import com.rafael.autenticacao.Usuario.Dto.UsuarioRequest;
import com.rafael.autenticacao.Usuario.Dto.UsuarioResponse;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> createUser(@Valid @RequestBody UsuarioRequest request) {
        Entidade user = new Entidade(
                null,
                request.name(),
                request.email(),
                request.password(),
                request.age(),
                Role.USER
        );

        Entidade savedUser = usuarioService.saveUser(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.fromEntity(savedUser));
    }

    @GetMapping
    public List<UsuarioResponse> findAllUsers() {
        return usuarioService.findAllUsers()
                .stream()
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse findUserById(@PathVariable UUID id) {
        return UsuarioResponse.fromEntity(usuarioService.findUserById(id));
    }

    @PatchMapping("/{id}")
    public UsuarioResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioPatchRequest request
    ) {
        Entidade updatedUser = usuarioService.updateUser(
                id,
                request.name(),
                request.email(),
                request.age()
        );

        return UsuarioResponse.fromEntity(updatedUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id) {
        usuarioService.deleteUser(id);
    }
}

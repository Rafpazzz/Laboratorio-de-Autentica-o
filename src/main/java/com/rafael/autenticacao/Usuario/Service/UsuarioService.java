package com.rafael.autenticacao.Usuario.Service;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Exceptions.ExistEmailException;
import com.rafael.autenticacao.Usuario.Exceptions.InvalidUserDataException;
import com.rafael.autenticacao.Usuario.Exceptions.UsuarioNotFoundException;
import com.rafael.autenticacao.Usuario.Repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Entidade saveUser(Entidade user) {
        if(usuarioRepository.existsByEmail(user.getEmail())) throw new ExistEmailException();

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return usuarioRepository.saveAndFlush(user);
    }

    public List<Entidade> findAllUsers() {
        return usuarioRepository.findAll();
    }

    public Entidade findUserById(UUID id) {
        return usuarioRepository.findById(id).orElseThrow(UsuarioNotFoundException::new);
    }

    public Entidade findUserByEmail(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow(UsuarioNotFoundException::new);
    }

    @Transactional
    public Entidade updateUser(UUID id, String name, String email, Integer age) {
        if (name == null && email == null && age == null) {
            throw new InvalidUserDataException("Informe ao menos um campo para atualizar");
        }

        Entidade user = findUserById(id);

        if (name != null) {
            user.setName(requiredText(name, "nome nao pode vir vazio"));
        }

        if (email != null) {
            String newEmail = requiredText(email, "email nao pode vir vazio");
            if (!newEmail.equals(user.getEmail()) && usuarioRepository.existsByEmail(newEmail)) {
                throw new ExistEmailException();
            }
            user.setEmail(newEmail);
        }

        if (age != null) {
            if (age < 0) {
                throw new InvalidUserDataException("idade nao pode ser negativa");
            }
            user.setAge(age);
        }

        return user;
    }

    public void deleteUser(UUID id) {
        Entidade user = findUserById(id);
        usuarioRepository.delete(user);
    }

    private String requiredText(String value, String message) {
        if (value.isBlank()) {
            throw new InvalidUserDataException(message);
        }

        return value.trim();
    }

}

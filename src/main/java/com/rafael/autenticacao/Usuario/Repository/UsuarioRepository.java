package com.rafael.autenticacao.Usuario.Repository;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Entidade, UUID> {

    Optional<Entidade> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);

}

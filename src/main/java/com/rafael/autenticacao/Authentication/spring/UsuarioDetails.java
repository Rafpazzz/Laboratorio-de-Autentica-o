package com.rafael.autenticacao.Authentication.spring;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioDetails implements UserDetails {


    private final Entidade entidade;

    public UsuarioDetails(Entidade entidade) {
        this.entidade = entidade;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority ="ROLE_" + entidade.getRole().name();

        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public @Nullable String getPassword() {
        return this.entidade.getPassword();
    }

    @Override
    public String getUsername() {
        return this.entidade.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}

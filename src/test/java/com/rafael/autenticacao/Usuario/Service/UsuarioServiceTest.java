package com.rafael.autenticacao.Usuario.Service;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Exceptions.ExistEmailException;
import com.rafael.autenticacao.Usuario.Exceptions.InvalidUserDataException;
import com.rafael.autenticacao.Usuario.Exceptions.UsuarioNotFoundException;
import com.rafael.autenticacao.Usuario.Repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String RAW_PASSWORD = "123456";
    private static final String HASHED_PASSWORD = "$2a$10$hash";

    private UsuarioService usuarioService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    @Test
    void saveUserShouldHashPasswordBeforeSaving() {
        Entidade user = user("Rafael", "rafael@email.com", RAW_PASSWORD, 30);

        when(usuarioRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(usuarioRepository.saveAndFlush(user)).thenReturn(user);

        Entidade savedUser = usuarioService.saveUser(user);

        ArgumentCaptor<Entidade> userCaptor = ArgumentCaptor.forClass(Entidade.class);
        verify(usuarioRepository).saveAndFlush(userCaptor.capture());

        Entidade userSentToRepository = userCaptor.getValue();
        assertThat(userSentToRepository.getPassword()).isEqualTo(HASHED_PASSWORD);
        assertThat(userSentToRepository.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(savedUser.getPassword()).isEqualTo(HASHED_PASSWORD);
    }

    @Test
    void saveUserShouldThrowWhenEmailAlreadyExists() {
        Entidade user = user("Rafael", "rafael@email.com", RAW_PASSWORD, 30);

        when(usuarioRepository.existsByEmail(user.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.saveUser(user))
                .isInstanceOf(ExistEmailException.class)
                .hasMessage("Email ja cadastrado");

        verify(passwordEncoder, never()).encode(RAW_PASSWORD);
        verify(usuarioRepository, never()).saveAndFlush(user);
    }

    @Test
    void findAllUsersShouldReturnRepositoryUsers() {
        List<Entidade> users = List.of(
                user("Rafael", "rafael@email.com", HASHED_PASSWORD, 30),
                user("Ana", "ana@email.com", HASHED_PASSWORD, 25)
        );

        when(usuarioRepository.findAll()).thenReturn(users);

        List<Entidade> foundUsers = usuarioService.findAllUsers();

        assertThat(foundUsers).containsExactlyElementsOf(users);
    }

    @Test
    void findUserByIdShouldReturnUserWhenUserExists() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        Entidade foundUser = usuarioService.findUserById(id);

        assertThat(foundUser).isSameAs(user);
    }

    @Test
    void findUserByIdShouldThrowWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.findUserById(id))
                .isInstanceOf(UsuarioNotFoundException.class)
                .hasMessage("Usuario nao encontrado");
    }

    @Test
    void findUserByEmailShouldReturnUserWhenUserExists() {
        String email = "rafael@email.com";
        Entidade user = user("Rafael", email, HASHED_PASSWORD, 30);

        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(user));

        Entidade foundUser = usuarioService.findUserByEmail(email);

        assertThat(foundUser).isSameAs(user);
    }

    @Test
    void findUserByEmailShouldThrowWhenUserDoesNotExist() {
        String email = "rafael@email.com";

        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.findUserByEmail(email))
                .isInstanceOf(UsuarioNotFoundException.class)
                .hasMessage("Usuario nao encontrado");
    }

    @Test
    void updateUserShouldUpdateProvidedFields() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));
        when(usuarioRepository.existsByEmail("novo@email.com")).thenReturn(false);

        Entidade updatedUser = usuarioService.updateUser(id, "Rafael Silva", "novo@email.com", 31);

        assertThat(updatedUser.getName()).isEqualTo("Rafael Silva");
        assertThat(updatedUser.getEmail()).isEqualTo("novo@email.com");
        assertThat(updatedUser.getAge()).isEqualTo(31);
        assertThat(updatedUser.getPassword()).isEqualTo(HASHED_PASSWORD);
    }

    @Test
    void updateUserShouldTrimTextFields() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));
        when(usuarioRepository.existsByEmail("novo@email.com")).thenReturn(false);

        Entidade updatedUser = usuarioService.updateUser(id, "  Rafael Silva  ", "  novo@email.com  ", null);

        assertThat(updatedUser.getName()).isEqualTo("Rafael Silva");
        assertThat(updatedUser.getEmail()).isEqualTo("novo@email.com");
        assertThat(updatedUser.getAge()).isEqualTo(30);
    }

    @Test
    void updateUserShouldNotCheckEmailUniquenessWhenEmailDoesNotChange() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        usuarioService.updateUser(id, "Rafael Silva", "rafael@email.com", null);

        verify(usuarioRepository, never()).existsByEmail("rafael@email.com");
    }

    @Test
    void updateUserShouldThrowWhenNoFieldIsProvided() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> usuarioService.updateUser(id, null, null, null))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage("Informe ao menos um campo para atualizar");

        verify(usuarioRepository, never()).findById(id);
    }

    @Test
    void updateUserShouldThrowWhenNameIsBlank() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usuarioService.updateUser(id, " ", null, null))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage("nome nao pode vir vazio");
    }

    @Test
    void updateUserShouldThrowWhenEmailIsBlank() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usuarioService.updateUser(id, null, " ", null))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage("email nao pode vir vazio");
    }

    @Test
    void updateUserShouldThrowWhenNewEmailAlreadyExists() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));
        when(usuarioRepository.existsByEmail("existente@email.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.updateUser(id, null, "existente@email.com", null))
                .isInstanceOf(ExistEmailException.class)
                .hasMessage("Email ja cadastrado");
    }

    @Test
    void updateUserShouldThrowWhenAgeIsNegative() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usuarioService.updateUser(id, null, null, -1))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage("idade nao pode ser negativa");
    }

    @Test
    void deleteUserShouldDeleteUserWhenUserExists() {
        UUID id = UUID.randomUUID();
        Entidade user = user(id, "Rafael", "rafael@email.com", HASHED_PASSWORD, 30);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(user));

        usuarioService.deleteUser(id);

        verify(usuarioRepository).delete(user);
    }

    @Test
    void deleteUserShouldThrowWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.deleteUser(id))
                .isInstanceOf(UsuarioNotFoundException.class)
                .hasMessage("Usuario nao encontrado");
    }

    private Entidade user(String name, String email, String password, int age) {
        return user(UUID.randomUUID(), name, email, password, age);
    }

    private Entidade user(UUID id, String name, String email, String password, int age) {
        return new Entidade(id, name, email, password, age, Role.USER);
    }
}

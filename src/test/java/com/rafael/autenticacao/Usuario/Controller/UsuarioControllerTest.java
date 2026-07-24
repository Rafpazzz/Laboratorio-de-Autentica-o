package com.rafael.autenticacao.Usuario.Controller;

import com.rafael.autenticacao.Shared.Exception.GlobalExceptionHandler;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Exceptions.ExistEmailException;
import com.rafael.autenticacao.Usuario.Exceptions.InvalidUserDataException;
import com.rafael.autenticacao.Usuario.Exceptions.UsuarioNotFoundException;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UsuarioController(usuarioService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createUserShouldReturnCreatedUserWithoutPassword() throws Exception {
        Entidade savedUser = user("Rafael", "rafael@email.com", "$2a$hash", 30);

        when(usuarioService.saveUser(any(Entidade.class))).thenReturn(savedUser);

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Rafael",
                                  "email": "rafael@email.com",
                                  "password": "123456",
                                  "age": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rafael"))
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<Entidade> userCaptor = ArgumentCaptor.forClass(Entidade.class);
        verify(usuarioService).saveUser(userCaptor.capture());

        Entidade receivedUser = userCaptor.getValue();
        assertThat(receivedUser.getPassword()).isEqualTo("123456");
        assertThat(receivedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createUserShouldReturnBadRequestWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "email-invalido",
                                  "password": "",
                                  "age": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Requisicao possui campos invalidos"))
                .andExpect(jsonPath("$.fields", hasSize(4)));
    }

    @Test
    void createUserShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        when(usuarioService.saveUser(any(Entidade.class))).thenThrow(new ExistEmailException());

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email ja cadastrado"));
    }

    @Test
    void createUserShouldReturnConflictWhenDatabaseIntegrityFails() throws Exception {
        when(usuarioService.saveUser(any(Entidade.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Operacao viola a integridade dos dados"));
    }

    @Test
    void findAllUsersShouldReturnUsers() throws Exception {
        when(usuarioService.findAllUsers()).thenReturn(List.of(
                user("Rafael", "rafael@email.com", "$2a$hash1", 30),
                user("Ana", "ana@email.com", "$2a$hash2", 25)
        ));

        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Rafael"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Ana"));
    }

    @Test
    void findUserByIdShouldReturnUser() throws Exception {
        UUID id = UUID.randomUUID();

        when(usuarioService.findUserById(id)).thenReturn(user("Rafael", "rafael@email.com", "$2a$hash", 30));

        mockMvc.perform(get("/usuarios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rafael"))
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findUserByIdShouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();

        when(usuarioService.findUserById(id)).thenThrow(new UsuarioNotFoundException());

        mockMvc.perform(get("/usuarios/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario nao encontrado"));
    }

    @Test
    void updateUserShouldReturnUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();

        when(usuarioService.updateUser(eq(id), eq("Rafael Silva"), eq("rafael.silva@email.com"), eq(31)))
                .thenReturn(user("Rafael Silva", "rafael.silva@email.com", "$2a$hash", 31));

        mockMvc.perform(patch("/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Rafael Silva",
                                  "email": "rafael.silva@email.com",
                                  "age": 31
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rafael Silva"))
                .andExpect(jsonPath("$.email").value("rafael.silva@email.com"))
                .andExpect(jsonPath("$.age").value(31))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateUserShouldReturnBadRequestWhenServiceRejectsPayload() throws Exception {
        UUID id = UUID.randomUUID();

        when(usuarioService.updateUser(eq(id), eq(null), eq(null), eq(null)))
                .thenThrow(new InvalidUserDataException("Informe ao menos um campo para atualizar"));

        mockMvc.perform(patch("/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Informe ao menos um campo para atualizar"));
    }

    @Test
    void deleteUserShouldReturnNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(usuarioService).deleteUser(id);

        mockMvc.perform(delete("/usuarios/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(usuarioService).deleteUser(id);
    }

    @Test
    void endpointShouldReturnInternalServerErrorForUnexpectedException() throws Exception {
        when(usuarioService.findAllUsers()).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno nao mapeado"))
                .andExpect(jsonPath("$.detail", not("unexpected")));
    }

    private String validCreateBody() {
        return """
                {
                  "name": "Rafael",
                  "email": "rafael@email.com",
                  "password": "123456",
                  "age": 30
                }
                """;
    }

    private Entidade user(String name, String email, String password, int age) {
        return new Entidade(UUID.randomUUID(), name, email, password, age, Role.USER);
    }
}

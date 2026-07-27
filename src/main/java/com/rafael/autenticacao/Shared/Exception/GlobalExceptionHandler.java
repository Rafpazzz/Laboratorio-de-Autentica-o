package com.rafael.autenticacao.Shared.Exception;

import com.rafael.autenticacao.Usuario.Exceptions.ExistEmailException;
import com.rafael.autenticacao.Usuario.Exceptions.InvalidUserDataException;
import com.rafael.autenticacao.Usuario.Exceptions.UsuarioNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.security.core.AuthenticationException;
import java.net.URI;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsuarioNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            UsuarioNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleRouteNotFound(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, "Recurso nao encontrado", request);
    }

    @ExceptionHandler(ExistEmailException.class)
    public ResponseEntity<ProblemDetail> handleExistingItem(
            ExistEmailException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, "Operacao viola a integridade dos dados", request);
    }

    @ExceptionHandler(InvalidUserDataException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUserData(
            InvalidUserDataException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail problem = buildProblemDetail(
                status,
                "Requisicao possui campos invalidos",
                request
        );

        problem.setProperty("fields", exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage()
                ))
                .toList());

        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Requisicao invalida", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        // Evita expor detalhes internos da aplicacao para o cliente.
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno nao mapeado", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handlerInvalidFields(AuthenticationException exception, HttpServletRequest servletRequest) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Credenciais invalidas", servletRequest);
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(status, message, request);

        return ResponseEntity.status(status).body(problem);
    }

    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));

        return problem;
    }


}

package com.astro.api.common.handler;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Recurso não encontrado. Ex: usuário inexistente.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResult.error(
                        ex.getMessage(),
                        List.of(ex.getMessage()),
                        request.getRequestURI()
                ));
    }

    // Violação de regra de negócio. Ex: gestor participando do próprio evento.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiResult.error(
                        ex.getMessage(),
                        List.of(ex.getMessage()),
                        request.getRequestURI()
                ));
    }

    // Conflito com dados existentes. Ex: e-mail já cadastrado.
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResult<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResult.error(
                        ex.getMessage(),
                        List.of(ex.getMessage()),
                        request.getRequestURI()
                ));
    }

    // Falha na validação. Ex: campo @NotBlank vazio.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage()
                )
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiResult.error(
                        "Erro de validação",
                        errors,
                        request.getRequestURI()
                ));
    }

    // Requisição ilegível. Ex: JSON malformado ou enum inválido.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiResult.error(
                        "Corpo da requisição inválido",
                        List.of("Verifique o formato dos dados enviados"),
                        request.getRequestURI()
                ));
    }

    // Violação no banco. Ex: UNIQUE ou FK.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResult.error(
                        "Conflito de integridade dos dados",
                        List.of("A operação viola uma restrição dos dados"),
                        request.getRequestURI()
                ));
    }

    // Erro inesperado. Ex: falha não tratada pela aplicação.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        LOGGER.error("Erro interno ao processar a requisição para {}", request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(
                        "Erro interno do servidor",
                        List.of("Ocorreu um erro inesperado"),
                        request.getRequestURI()
                ));
    }
}

package com.astro.api.common.handler;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.common.response.ApiResult;
import com.mongodb.MongoTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SpreadsheetImportException.class)
    public ResponseEntity<ApiResult<List<SpreadsheetImportMessage>>> handleSpreadsheet(
            SpreadsheetImportException exception, HttpServletRequest request) {
        ApiResult<List<SpreadsheetImportMessage>> result = new ApiResult<>(false, exception.getMessage(),
                exception.getImportErrors(), List.of("Corrija as linhas indicadas e envie novamente"),
                request.getRequestURI());
        return ResponseEntity.status(422).body(result);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidParameter(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResult.error(
                "Parâmetro da requisição inválido",
                List.of("Verifique os parâmetros obrigatórios e seus tipos"), request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResult.error(
                "Não autorizado", List.of("Você não tem permissão para executar esta operação"),
                request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiResult.error("Upload excede o tamanho permitido",
                        List.of("O arquivo ou a requisição excede o limite permitido para este endpoint."),
                        request.getRequestURI()));
    }

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResult.error(
                "Parâmetro da requisição inválido",
                List.of("Verifique o valor do parâmetro '" + ex.getName() + "'"),
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

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingMultipartPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResult.error(
                "Parte obrigatória da requisição ausente",
                List.of("A parte '" + ex.getRequestPartName() + "' é obrigatória"),
                request.getRequestURI()
        ));
    }

    // Método HTTP não permitido para a rota. Ex: POST enviado para endpoint que aceita apenas GET.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String allowedMethods = ex.getSupportedHttpMethods().stream()
                .map(method -> method.name())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", allowedMethods)
                .body(ApiResult.error(
                        "Método HTTP não permitido",
                        List.of("Use um dos métodos permitidos: " + allowedMethods),
                        request.getRequestURI()
                ));
    }

    // Banco indisponível ou conexão esgotada: PostgreSQL, MongoDB ou Redis.
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            DataAccessResourceFailureException.class,
            RedisConnectionFailureException.class,
            MongoTimeoutException.class
    })
    public ResponseEntity<ApiResult<Void>> handleDatabaseConnectionFailure(Exception ex, HttpServletRequest request) {
        LOGGER.error("Falha de conexão com banco de dados ao processar {}", request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResult.error(
                        "Serviço de dados temporariamente indisponível",
                        List.of("Não foi possível conectar ao banco de dados. Tente novamente em instantes"),
                        request.getRequestURI()
                ));
    }

    // Violação no banco. Ex: UNIQUE ou FK.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        LOGGER.warn("Conflito de integridade ao processar {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
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

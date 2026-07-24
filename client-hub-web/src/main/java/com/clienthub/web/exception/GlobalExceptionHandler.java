package com.clienthub.web.exception;

import com.clienthub.application.exception.InvalidInvoiceStateException;
import com.clienthub.application.exception.InvalidTaskStateException;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.web.dto.common.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API
 * Catches exceptions and returns consistent error responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        log.warn("Validation error: {}", errors);

        ErrorResponse response = new ErrorResponse(
                "Validation Failed",
                "Invalid request data. Please check the fields and try again.",
                HttpStatus.BAD_REQUEST.value(),
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Authentication Failed",
                "Invalid email or password",
                HttpStatus.UNAUTHORIZED.value()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UsernameNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "User Not Found",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Invalid Request",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({
        InvalidTaskStateException.class,
        InvalidInvoiceStateException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidState(RuntimeException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Invalid State Transition",
                ex.getMessage(),
                HttpStatus.CONFLICT.value()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request payload: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Invalid Request Body",
                "Request payload format is invalid or contains unsupported values.",
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({
        ResourceNotFoundException.class,
        TaskNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Not Found",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No route or static resource found: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Not Found",
                "The requested endpoint was not found",
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({
        AuthorizationDeniedException.class,
        org.springframework.security.access.AccessDeniedException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(Exception ex) {
        log.warn("Authorization denied: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Access Denied",
                "You do not have permission to access this resource",
                HttpStatus.FORBIDDEN.value()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "Access Denied",
                "You do not have permission to access this resource",
                HttpStatus.FORBIDDEN.value()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(com.clienthub.infrastructure.exception.PdfProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePdfProcessing(com.clienthub.infrastructure.exception.PdfProcessingException ex) {
        log.warn("PDF processing error: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "PDF Processing Failed",
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.value()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(com.clienthub.infrastructure.exception.AiServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(com.clienthub.infrastructure.exception.AiServiceUnavailableException ex) {
        log.error("AI service unavailable: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                "AI Service Unavailable",
                "The AI service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse response = new ErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

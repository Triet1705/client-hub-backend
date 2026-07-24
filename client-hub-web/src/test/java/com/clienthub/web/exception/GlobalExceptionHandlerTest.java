package com.clienthub.web.exception;

import com.clienthub.application.exception.InvalidInvoiceStateException;
import com.clienthub.application.exception.InvalidTaskStateException;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.web.dto.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNoResourceFoundReturnsNotFound() {
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "/api/projects/missing/files"
        );

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void controlledTaskAndInvoiceStateErrorsReturnConflict() {
        ResponseEntity<ErrorResponse> taskResponse = handler.handleInvalidState(
                new InvalidTaskStateException(
                        UUID.randomUUID(), TaskStatus.TODO, TaskStatus.DONE));
        ResponseEntity<ErrorResponse> invoiceResponse = handler.handleInvalidState(
                new InvalidInvoiceStateException(
                        "Invalid state transition from DRAFT to PAID"));

        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(taskResponse.getBody()).isNotNull();
        assertThat(taskResponse.getBody().getError()).isEqualTo("Invalid State Transition");
        assertThat(invoiceResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invoiceResponse.getBody()).isNotNull();
        assertThat(invoiceResponse.getBody().getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void unexpectedProgrammingErrorRemainsSanitizedInternalServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericError(
                new IllegalStateException("sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive");
    }
}

package com.wattsmart.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ForbiddenException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.common.service.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainExceptionsToExpectedStatuses() {
        assertStatus(handler.handleBadRequest(new BadRequestException("bad request")), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleUnauthorized(new UnauthorizedException("missing token")), HttpStatus.UNAUTHORIZED);
        assertStatus(handler.handleForbidden(new ForbiddenException("not allowed")), HttpStatus.FORBIDDEN);
        assertStatus(handler.handleNotFound(new ResourceNotFoundException("missing")), HttpStatus.NOT_FOUND);
    }

    @Test
    void masksDatabaseAndUnexpectedErrorsAsInternalServerErrors() {
        ResponseEntity<ApiErrorResponse> databaseResponse =
                handler.handleDataAccess(new DataIntegrityViolationException("constraint leaked detail"));
        ResponseEntity<ApiErrorResponse> unexpectedResponse =
                handler.handleUnexpected(new IllegalStateException("implementation detail"));

        assertStatus(databaseResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(databaseResponse.getBody()).isNotNull();
        assertThat(databaseResponse.getBody().message()).isEqualTo("A database operation failed unexpectedly.");

        assertStatus(unexpectedResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unexpectedResponse.getBody()).isNotNull();
        assertThat(unexpectedResponse.getBody().message()).isEqualTo("An unexpected server error occurred.");
    }

    private void assertStatus(ResponseEntity<ApiErrorResponse> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().error()).isEqualTo(status.getReasonPhrase());
    }
}

/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorResponse(String error, Instant timestamp) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        log.warn(
                "event=bad_request error_type=IllegalArgumentException reason={}",
                exception.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid request parameters", Instant.now()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException exception) {
        log.warn(
                "event=bad_request error_type=MissingServletRequestParameterException param={}",
                exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(
                        new ErrorResponse(
                                "Missing required parameter: " + exception.getParameterName(),
                                Instant.now()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        log.warn(
                "event=bad_request error_type=MethodArgumentTypeMismatchException param={}",
                exception.getName());
        return ResponseEntity.badRequest()
                .body(
                        new ErrorResponse(
                                "Invalid parameter format: " + exception.getName(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception) {
        log.error(
                "event=unhandled_exception error_type=Exception message={}",
                exception.getMessage(),
                exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred", Instant.now()));
    }
}

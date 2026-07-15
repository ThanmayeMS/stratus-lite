package com.stratuslite.common;

import com.stratuslite.placement.NoPlacementFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(NoPlacementFoundException.class)
    public ResponseEntity<ApiError> placementConflict(NoPlacementFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}


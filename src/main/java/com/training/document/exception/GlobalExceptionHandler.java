package com.training.document.exception;

import com.training.document.dto.ValidationError;
import com.training.document.dto.ValidationErrors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

import java.util.List;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrors handleValidationException(MethodArgumentNotValidException e) {
        List<ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .toList();
        log.info("Validation failed: {}", errors);
        return new ValidationErrors(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrors handleConstraintViolation(ConstraintViolationException e) {
        List<ValidationError> errors = e.getConstraintViolations().stream()
                .map(this::toValidationError)
                .toList();
        log.info("Validation failed: {}", errors);
        return new ValidationErrors(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrors handleMalformedRequest(HttpMessageNotReadableException e) {
        ValidationError error = toValidationError(e);
        log.info("Validation failed: {}", error);
        return new ValidationErrors(List.of(error));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationError handleNotFound(DocumentNotFoundException e) {
        log.info(e.getMessage());
        return new ValidationError("not.found", e.getMessage());
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError("invalid." + fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ValidationError toValidationError(ConstraintViolation<?> violation) {
        return new ValidationError("invalid." + toFieldName(violation.getPropertyPath()), violation.getMessage());
    }

    // Builds the field name from the PROPERTY nodes of the path (e.g. "items[0].name"), falling back to the
    // PARAMETER node name for violations on plain method parameters (e.g. "drn").
    private String toFieldName(Path propertyPath) {
        StringBuilder fieldName = new StringBuilder();
        String parameterName = null;
        for (Path.Node node : propertyPath) {
            if (node.getKind() == ElementKind.PROPERTY) {
                if (node.getIndex() != null) {
                    fieldName.append('[').append(node.getIndex()).append("].").append(node.getName());
                } else {
                    if (!fieldName.isEmpty()) {
                        fieldName.append('.');
                    }
                    fieldName.append(node.getName());
                }
            } else if (node.getKind() == ElementKind.PARAMETER) {
                parameterName = node.getName();
            }
        }
        return !fieldName.isEmpty() ? fieldName.toString() : parameterName;
    }

    private ValidationError toValidationError(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof JacksonException jacksonException && !jacksonException.getPath().isEmpty()) {
            String field = jacksonException.getPath().get(jacksonException.getPath().size() - 1).getPropertyName();
            return new ValidationError("invalid." + field, "Invalid value");
        }
        return new ValidationError("invalid.request", "Malformed request body");
    }
}

package com.naesan.evidence.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.naesan.account.adapter.in.web.ApiErrorResponse;
import com.naesan.evidence.application.EvidenceException;
import com.naesan.evidence.application.EvidenceFileException;

@RestControllerAdvice(assignableTypes = EvidenceApiController.class)
public class EvidenceApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new ApiErrorResponse(
                "INVALID_REQUEST",
                "요청 필드를 확인해 주세요.",
                fieldErrors
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleInvalidInput(IllegalArgumentException exception) {
        return new ApiErrorResponse(
                "INVALID_EVIDENCE_INPUT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(EvidenceFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleFileValidation(EvidenceFileException exception) {
        return new ApiErrorResponse(
                exception.code().name(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(EvidenceException.class)
    ResponseEntity<ApiErrorResponse> handleEvidence(
            EvidenceException exception
    ) {
        HttpStatus status = switch (exception.code()) {
            case EVIDENCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EVIDENCE_NOT_EDITABLE, FILE_ALREADY_ATTACHED, CONCURRENT_MODIFICATION ->
                    HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        exception.code().name(),
                        exception.getMessage()
                ));
    }
}

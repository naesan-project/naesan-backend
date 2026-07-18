package com.naesan.passport.adapter.in.web;

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
import com.naesan.passport.application.PassportException;

@RestControllerAdvice(assignableTypes = PassportApiController.class)
public class PassportApiExceptionHandler {

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

    @ExceptionHandler(PassportException.class)
    ResponseEntity<ApiErrorResponse> handlePassport(PassportException exception) {
        HttpStatus status = switch (exception.code()) {
            case PASSPORT_SOURCE_NOT_FOUND, PASSPORT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case PASSPORT_ALREADY_ISSUED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        exception.code().name(),
                        exception.getMessage()
                ));
    }
}

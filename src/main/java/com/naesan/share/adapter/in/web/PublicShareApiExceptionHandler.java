package com.naesan.share.adapter.in.web;

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
import com.naesan.share.application.PublicShareException;

@RestControllerAdvice(assignableTypes = PublicShareManagementApiController.class)
public class PublicShareApiExceptionHandler {

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

    @ExceptionHandler(PublicShareException.class)
    ResponseEntity<ApiErrorResponse> handlePublicShare(
            PublicShareException exception
    ) {
        HttpStatus status = switch (exception.code()) {
            case PUBLIC_SHARE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PUBLIC_SHARE_ALREADY_ACTIVE -> HttpStatus.CONFLICT;
            case PUBLIC_FILE_EMPTY,
                    PUBLIC_FILE_TOO_LARGE,
                    PUBLIC_FILE_UNSUPPORTED,
                    PUBLIC_FILE_TYPE_MISMATCH,
                    PUBLIC_FILE_READ_FAILED -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        exception.code().name(),
                        exception.getMessage()
                ));
    }
}

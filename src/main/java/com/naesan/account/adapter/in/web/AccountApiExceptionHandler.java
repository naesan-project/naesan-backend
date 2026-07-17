package com.naesan.account.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.naesan.account.application.AccountException;

@RestControllerAdvice(assignableTypes = AccountApiController.class)
public class AccountApiExceptionHandler {
    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_ACCOUNT_INPUT_CODE = "INVALID_ACCOUNT_INPUT";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        return new ApiErrorResponse(
                INVALID_REQUEST_CODE,
                "요청 필드를 확인해 주세요.",
                fieldErrors(exception)
        );
    }

    private Map<String, String> fieldErrors(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return fieldErrors;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleUnreadableRequest() {
        return new ApiErrorResponse(
                INVALID_REQUEST_CODE,
                "요청 본문을 읽을 수 없습니다."
        );
    }

    @ExceptionHandler(AccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse handleAccountException(AccountException exception) {
        return new ApiErrorResponse(
                exception.code().name(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleInvalidAccountInput(IllegalArgumentException exception) {
        return new ApiErrorResponse(
                INVALID_ACCOUNT_INPUT_CODE,
                exception.getMessage()
        );
    }
}

package com.naesan.transfer.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.naesan.account.adapter.in.web.ApiErrorResponse;
import com.naesan.transfer.application.TransferException;

@RestControllerAdvice(assignableTypes = {
        TransferApiController.class,
        TransferManagementApiController.class
})
public class TransferApiExceptionHandler {

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleUnreadableRequest() {
        return new ApiErrorResponse(
                "INVALID_REQUEST",
                "요청 본문을 읽을 수 없습니다."
        );
    }

    @ExceptionHandler(TransferException.class)
    ResponseEntity<ApiErrorResponse> handleTransfer(TransferException exception) {
        HttpStatus status = switch (exception.code()) {
            case TRANSFER_NOT_FOUND, TRANSFER_RECIPIENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TRANSFER_ALREADY_PENDING,
                    TRANSFER_NOT_PENDING,
                    TRANSFER_HOLDER_CHANGED -> HttpStatus.CONFLICT;
            case TRANSFER_INVALID_RECIPIENT, TRANSFER_SELF_REQUEST -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        exception.code().name(),
                        exception.getMessage()
                ));
    }
}

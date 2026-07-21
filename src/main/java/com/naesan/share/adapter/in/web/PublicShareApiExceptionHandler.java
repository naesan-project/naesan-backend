package com.naesan.share.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.naesan.account.adapter.in.web.ApiErrorResponse;
import com.naesan.share.application.PublicShareErrorCode;
import com.naesan.share.application.PublicShareException;

@RestControllerAdvice(assignableTypes = {
        PublicShareManagementApiController.class,
        PublicVerificationApiController.class
})
public class PublicShareApiExceptionHandler {
    private static final String PUBLIC_API_PREFIX = "/api/public/";
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";

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
            PublicShareException exception,
            HttpServletRequest request
    ) {
        if (request.getRequestURI().startsWith(PUBLIC_API_PREFIX)) {
            return publicError(exception);
        }
        return managementError(exception);
    }

    private ResponseEntity<ApiErrorResponse> publicError(
            PublicShareException exception
    ) {
        boolean notFound = exception.code()
                == PublicShareErrorCode.PUBLIC_SHARE_NOT_FOUND;
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String code = notFound ? "PUBLIC_SHARE_NOT_FOUND" : exception.code().name();
        String message = notFound
                ? "Public share를 찾을 수 없습니다."
                : exception.getMessage();
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(REFERRER_POLICY_HEADER, "no-referrer")
                .body(new ApiErrorResponse(code, message));
    }

    private ResponseEntity<ApiErrorResponse> managementError(
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

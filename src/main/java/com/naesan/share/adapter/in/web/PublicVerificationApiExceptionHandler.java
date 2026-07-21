package com.naesan.share.adapter.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.naesan.account.adapter.in.web.ApiErrorResponse;
import com.naesan.share.application.PublicShareException;

@RestControllerAdvice(assignableTypes = PublicVerificationApiController.class)
public class PublicVerificationApiExceptionHandler {
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";

    @ExceptionHandler(PublicShareException.class)
    ResponseEntity<ApiErrorResponse> handlePublicShare(
            PublicShareException exception
    ) {
        boolean notFound = exception.code()
                == com.naesan.share.application.PublicShareErrorCode.PUBLIC_SHARE_NOT_FOUND;
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String code = notFound ? "PUBLIC_SHARE_NOT_FOUND" : exception.code().name();
        String message = notFound
                ? "Public share를 찾을 수 없습니다."
                : exception.getMessage();
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .body(new ApiErrorResponse(
                        code,
                        message
                ));
    }
}

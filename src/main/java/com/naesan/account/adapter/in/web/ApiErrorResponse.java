package com.naesan.account.adapter.in.web;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors
) {

    public ApiErrorResponse(String code, String message) {
        this(code, message, Map.of());
    }
}

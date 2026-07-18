package com.naesan.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/csrf")
public class CsrfApiController {

    @GetMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrfToken(CsrfToken csrfToken) {
        csrfToken.getToken();
    }
}

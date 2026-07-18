package com.naesan.security;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import com.naesan.account.domain.Account;

public class AccountSessionManager {
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AccountSessionManager(
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository);
        this.sessionAuthenticationStrategy =
                Objects.requireNonNull(sessionAuthenticationStrategy);
    }

    public AuthenticatedAccount startAuthenticatedSession(
            Account account,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedAccount principal = AuthenticatedAccount.from(account);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of()
        );
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        return principal;
    }
}

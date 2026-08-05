package com.naesan.security;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.naesan.account.application.port.out.AccountRepository;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final AccountRepository accountRepository;

    public ActiveAccountFilter(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean accountIsActive = accountRepository.findById(account.id())
                .filter(foundAccount -> foundAccount.canAuthenticate())
                .isPresent();
        if (accountIsActive) {
            filterChain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.clearContext();
        response.sendError(HttpStatus.UNAUTHORIZED.value());
    }
}

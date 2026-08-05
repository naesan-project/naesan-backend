package com.naesan.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.HttpStatusAccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.operations.HttpRequestCorrelationFilter;
import com.naesan.share.adapter.in.web.PublicVerificationRateLimitFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            AccountRepository accountRepository,
            PublicVerificationRateLimitFilter publicVerificationRateLimitFilter
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/accounts")
                        .ignoringRequestMatchers(
                                "/api/public/passport-verification/file-match"
                        )
                        .ignoringRequestMatchers(request -> {
                            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                            return authorization != null
                                    && authorization.startsWith("Bearer ");
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessions").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sessions/refresh"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/sessions/current"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health", "/ready").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/public/passport-verification"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/public/passport-verification/file-match"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new JwtAuthenticatedAccountConverter()
                        ))
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.FORBIDDEN))
                )
                .requestCache(requestCache -> requestCache.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .addFilterAfter(
                        publicVerificationRateLimitFilter,
                        CorsFilter.class
                )
                .addFilterAfter(
                        new ActiveAccountFilter(accountRepository),
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${naesan.security.frontend-origin}") String frontendOrigin
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-XSRF-TOKEN",
                "X-Public-Share-Token"
        ));
        configuration.setExposedHeaders(List.of(
                HttpRequestCorrelationFilter.REQUEST_ID_HEADER
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }
}

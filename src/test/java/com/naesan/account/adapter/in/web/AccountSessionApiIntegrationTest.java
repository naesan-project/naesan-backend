package com.naesan.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class AccountSessionApiIntegrationTest {
    private static final String SESSIONS_API = "/api/sessions";
    private static final String REFRESH_API = SESSIONS_API + "/refresh";
    private static final String CURRENT_SESSION_API = SESSIONS_API + "/current";
    private static final String CSRF_API = "/api/csrf";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REFRESH_COOKIE = "NAESAN_REFRESH_TOKEN";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password1234";

    private final MockMvc mockMvc;
    private final RegisterAccountService registerAccountService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AccountSessionApiIntegrationTest(
            MockMvc mockMvc,
            RegisterAccountService registerAccountService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.registerAccountService = registerAccountService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("CSRF token을 frontend가 읽을 수 있는 cookie로 발급한다")
    void issuesCsrfTokenForFrontend() throws Exception {
        mockMvc.perform(get(CSRF_API)
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        FRONTEND_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(cookie().exists(CSRF_COOKIE))
                .andExpect(cookie().httpOnly(CSRF_COOKIE, false));
    }

    @Test
    @DisplayName("허용한 frontend origin의 Authorization preflight를 승인한다")
    void allowsAuthorizationPreflightFromFrontend() throws Exception {
        mockMvc.perform(options(CURRENT_SESSION_API)
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        FRONTEND_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    @Test
    @DisplayName("설정하지 않은 origin의 preflight를 거절한다")
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options(SESSIONS_API)
                        .header(HttpHeaders.ORIGIN, "https://unknown.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("정상 자격증명은 JWT access token과 HttpOnly refresh token을 발급한다")
    void issuesAccessAndRefreshTokens() throws Exception {
        Account account = registerAccountService.register(EMAIL, RAW_PASSWORD);

        MvcResult loginResult = login("  User@Example.COM  ", RAW_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, CURRENT_SESSION_API))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isString())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andReturn();

        String accessToken = json(loginResult, "$.accessToken");
        mockMvc.perform(get(CURRENT_SESSION_API)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("Bearer token이 없는 현재 계정 조회는 401을 반환한다")
    void rejectsCurrentSessionWithoutBearerToken() throws Exception {
        mockMvc.perform(get(CURRENT_SESSION_API))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("틀린 비밀번호는 평문을 노출하지 않고 401로 거절한다")
    void rejectsWrongPasswordWithoutExposingPassword() throws Exception {
        String wrongPassword = "different-password";
        registerAccountService.register(EMAIL, RAW_PASSWORD);

        login(EMAIL, wrongPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andExpect(content().string(not(containsString(wrongPassword))));
    }

    @Test
    @DisplayName("빈 로그인 필드는 400 field 오류로 반환한다")
    void rejectsBlankLoginFields() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.email")
                        .value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("비밀번호를 입력해 주세요."));
    }

    @Test
    @DisplayName("CSRF token이 없는 로그인 요청은 403으로 거절한다")
    void rejectsLoginWithoutCsrfToken() throws Exception {
        registerAccountService.register(EMAIL, RAW_PASSWORD);

        mockMvc.perform(post(SESSIONS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionRequest(EMAIL, RAW_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Refresh token은 1회 사용 후 회전하고 이전 token 재사용을 거절한다")
    void rotatesRefreshTokenAndRejectsReuse() throws Exception {
        registerAccountService.register(EMAIL, RAW_PASSWORD);
        MvcResult loginResult = login(EMAIL, RAW_PASSWORD).andReturn();
        Cookie firstRefreshToken = requiredCookie(loginResult, REFRESH_COOKIE);
        Cookie csrfCookie = issueCsrfCookie();

        MvcResult refreshResult = mockMvc.perform(post(REFRESH_API)
                        .cookie(firstRefreshToken, csrfCookie)
                        .header(CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andReturn();
        Cookie rotatedRefreshToken = requiredCookie(refreshResult, REFRESH_COOKIE);
        assertThat(rotatedRefreshToken.getValue())
                .isNotEqualTo(firstRefreshToken.getValue());

        mockMvc.perform(post(REFRESH_API)
                        .cookie(firstRefreshToken, csrfCookie)
                        .header(CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃은 refresh token을 폐기하고 cookie를 삭제한다")
    void revokesRefreshTokenOnLogout() throws Exception {
        registerAccountService.register(EMAIL, RAW_PASSWORD);
        MvcResult loginResult = login(EMAIL, RAW_PASSWORD).andReturn();
        Cookie refreshToken = requiredCookie(loginResult, REFRESH_COOKIE);
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(delete(CURRENT_SESSION_API)
                        .cookie(refreshToken, csrfCookie)
                        .header(CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        mockMvc.perform(post(REFRESH_API)
                        .cookie(refreshToken, csrfCookie)
                        .header(CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF token이 없는 로그아웃은 403이며 refresh token을 유지한다")
    void rejectsLogoutWithoutCsrfToken() throws Exception {
        registerAccountService.register(EMAIL, RAW_PASSWORD);
        MvcResult loginResult = login(EMAIL, RAW_PASSWORD).andReturn();
        Cookie refreshToken = requiredCookie(loginResult, REFRESH_COOKIE);

        mockMvc.perform(delete(CURRENT_SESSION_API).cookie(refreshToken))
                .andExpect(status().isForbidden());

        Cookie csrfCookie = issueCsrfCookie();
        mockMvc.perform(post(REFRESH_API)
                        .cookie(refreshToken, csrfCookie)
                        .header(CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        Cookie csrfCookie = issueCsrfCookie();
        return mockMvc.perform(post(SESSIONS_API)
                .cookie(csrfCookie)
                .header(CSRF_HEADER, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionRequest(email, password)));
    }

    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_API))
                .andExpect(status().isNoContent())
                .andReturn();
        return requiredCookie(result, CSRF_COOKIE);
    }

    private Cookie requiredCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String sessionRequest(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}

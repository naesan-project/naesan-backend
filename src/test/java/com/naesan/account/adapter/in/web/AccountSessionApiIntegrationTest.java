package com.naesan.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class AccountSessionApiIntegrationTest {
    private static final String SESSIONS_API = "/api/sessions";
    private static final String CURRENT_SESSION_API = SESSIONS_API + "/current";
    private static final String CSRF_API = "/api/csrf";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
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
    void deleteAccounts() {
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
    @DisplayName("허용한 frontend origin의 credential preflight를 승인한다")
    void allowsCredentialPreflightFromFrontend() throws Exception {
        mockMvc.perform(options(SESSIONS_API)
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-xsrf-token"
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
    @DisplayName("설정하지 않은 origin의 credential preflight를 거절한다")
    void rejectsCredentialPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options(SESSIONS_API)
                        .header(HttpHeaders.ORIGIN, "https://unknown.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("정상 자격증명은 session ID를 교체하고 현재 계정을 인증한다")
    void createsAuthenticatedSessionAndChangesSessionId() throws Exception {
        Account account = registerAccountService.register(EMAIL, RAW_PASSWORD);
        MockHttpSession session = new MockHttpSession();
        String sessionIdBeforeLogin = session.getId();

        login(session, "  User@Example.COM  ", RAW_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, CURRENT_SESSION_API))
                .andExpect(cookie().maxAge(CSRF_COOKIE, 0))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

        mockMvc.perform(get(CURRENT_SESSION_API).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("비회원의 현재 session 조회는 401을 반환한다")
    void rejectsCurrentSessionWithoutAuthentication() throws Exception {
        mockMvc.perform(get(CURRENT_SESSION_API))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("틀린 비밀번호는 평문을 노출하지 않고 401로 거절한다")
    void rejectsWrongPasswordWithoutExposingPassword() throws Exception {
        String wrongPassword = "different-password";
        registerAccountService.register(EMAIL, RAW_PASSWORD);

        login(new MockHttpSession(), EMAIL, wrongPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andExpect(content().string(not(containsString(wrongPassword))));
    }

    @Test
    @DisplayName("미등록 이메일도 같은 401 자격증명 오류로 거절한다")
    void rejectsUnknownEmailWithSameCredentialsError() throws Exception {
        login(new MockHttpSession(), "unknown@example.com", RAW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("빈 로그인 필드는 400 field 오류로 반환한다")
    void rejectsBlankLoginFields() throws Exception {
        login(new MockHttpSession(), "", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.email").value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.password").value("비밀번호를 입력해 주세요."));
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

    private ResultActions login(
            MockHttpSession session,
            String email,
            String password
    ) throws Exception {
        MvcResult csrfResult = issueCsrfToken(session);
        Cookie csrfCookie = csrfResult.getResponse().getCookie(CSRF_COOKIE);
        assertThat(csrfCookie).isNotNull();

        return mockMvc.perform(post(SESSIONS_API)
                .session(session)
                .cookie(csrfCookie)
                .header(CSRF_HEADER, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionRequest(email, password)));
    }

    private MvcResult issueCsrfToken(MockHttpSession session) throws Exception {
        return mockMvc.perform(get(CSRF_API).session(session))
                .andExpect(status().isNoContent())
                .andReturn();
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

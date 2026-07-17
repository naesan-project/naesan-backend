package com.naesan.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class RegisterAccountApiIntegrationTest {
    private static final String ACCOUNTS_API = "/api/accounts";
    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password1234";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    RegisterAccountApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void deleteAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("JSON 회원가입 요청을 저장하고 생성된 계정을 반환한다")
    void registersAccountAndReturnsCreatedResponse() throws Exception {
        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("  User@Example.COM  ", RAW_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(ACCOUNTS_API + "/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.createdAt").isString());

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM accounts WHERE email = ?",
                String.class,
                EMAIL
        );
        assertThat(passwordHash).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, passwordHash)).isTrue();
    }

    @Test
    @DisplayName("비회원이 보호된 API를 요청하면 401을 반환한다")
    void returnsUnauthorizedForProtectedApi() throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @WithMockUser
    @DisplayName("CSRF token이 없는 보호 API 요청은 403을 반환한다")
    void returnsForbiddenForProtectedApiWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/private")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("빈 필드를 400 오류 응답으로 반환한다")
    void returnsValidationProblemForBlankFields() throws Exception {
        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.email").value("이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.password").value("비밀번호를 입력해 주세요."));

        assertThat(countAccounts(EMAIL)).isZero();
    }

    @Test
    @DisplayName("읽을 수 없는 JSON을 400 오류 응답으로 반환한다")
    void returnsProblemForUnreadableJson() throws Exception {
        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));

        assertThat(countAccounts(EMAIL)).isZero();
    }

    @Test
    @DisplayName("비밀번호 정책 오류를 400으로 반환하고 평문을 응답하지 않는다")
    void returnsPasswordPolicyProblemWithoutRawPassword() throws Exception {
        String invalidPassword = "short";

        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest(EMAIL, invalidPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_INPUT"))
                .andExpect(jsonPath("$.message").value("비밀번호는 12~64 UTF-8 byte여야 합니다."))
                .andExpect(content().string(not(containsString(invalidPassword))));

        assertThat(countAccounts(EMAIL)).isZero();
    }

    @Test
    @DisplayName("중복 이메일을 409 오류 응답으로 반환하고 계정을 추가하지 않는다")
    void returnsConflictForDuplicateEmail() throws Exception {
        registerAccount();

        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("User@Example.com", RAW_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("이미 등록된 이메일입니다."));

        assertThat(countAccounts(EMAIL)).isOne();
    }

    private void registerAccount() throws Exception {
        mockMvc.perform(post(ACCOUNTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest(EMAIL, RAW_PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String accountRequest(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private int countAccounts(String email) {
        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ?",
                Integer.class,
                email
        );
        return accountCount == null ? 0 : accountCount;
    }
}

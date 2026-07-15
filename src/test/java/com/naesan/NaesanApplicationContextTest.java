package com.naesan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NaesanApplicationContextTest {

    @Test
    @DisplayName("PostgreSQL과 연결된 애플리케이션 컨텍스트가 정상적으로 시작된다")
    void loadsApplicationContextWithPostgreSQL() {
    }
}

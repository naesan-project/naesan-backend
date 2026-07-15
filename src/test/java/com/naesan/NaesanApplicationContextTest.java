package com.naesan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NaesanApplicationContextTest {
    @Autowired
    private Environment environment;

    @Test
    @DisplayName("PostgreSQL과 연결된 애플리케이션 컨텍스트가 정상적으로 시작된다")
    void loadsApplicationContextWithPostgreSQL() {
    }

    @Test
    @DisplayName("JPA는 View에서 영속성 컨텍스트를 열지 않고 스키마를 검증만 한다")
    void appliesJpaSafetySettings() {
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}

package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class HttpRequestCorrelationFilterTest {

    @Test
    @DisplayName("API 응답과 완료 log가 동일한 server request ID를 공유한다")
    void correlatesResponseAndCompletionLog() throws Exception {
        HttpRequestCorrelationFilter filter = new HttpRequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/csrf"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(
                HttpRequestCorrelationFilter.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            filter.doFilter(
                    request,
                    response,
                    (servletRequest, servletResponse) ->
                            ((MockHttpServletResponse) servletResponse)
                                    .setStatus(200)
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String requestId = response.getHeader(
                HttpRequestCorrelationFilter.REQUEST_ID_HEADER
        );
        UUID.fromString(requestId);
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("HTTP request completed");
                    assertThat(event.getMDCPropertyMap())
                            .containsEntry("request_id", requestId);
                });
    }
}

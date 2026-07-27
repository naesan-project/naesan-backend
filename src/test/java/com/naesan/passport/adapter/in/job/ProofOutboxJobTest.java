package com.naesan.passport.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.naesan.passport.application.ProcessProofOutboxService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ProofOutboxJobTest {

    @Test
    @DisplayName("Pending event가 없으면 현재 batch 처리를 멈춘다")
    void stopsWhenNoPendingEventRemains() {
        ProcessProofOutboxService service = mock(ProcessProofOutboxService.class);
        when(service.processNext("worker-1"))
                .thenReturn(true, true, false);
        ProofOutboxJob job = new ProofOutboxJob(service, "worker-1", 10);

        job.processPendingEvents();

        verify(service, times(3)).processNext("worker-1");
        verify(service).refreshStatusMetrics();
    }

    @Test
    @DisplayName("한 번의 실행에서 설정한 batch 크기만 처리한다")
    void respectsBatchSize() {
        ProcessProofOutboxService service = mock(ProcessProofOutboxService.class);
        when(service.processNext("worker-1")).thenReturn(true);
        ProofOutboxJob job = new ProofOutboxJob(service, "worker-1", 2);

        job.processPendingEvents();

        verify(service, times(2)).processNext("worker-1");
        verify(service).refreshStatusMetrics();
    }

    @Test
    @DisplayName("처리한 batch log에 실행마다 새 worker correlation ID를 남긴다")
    void correlatesProcessedBatchLog() {
        ProcessProofOutboxService service = mock(ProcessProofOutboxService.class);
        when(service.processNext("worker-1")).thenReturn(true, false);
        ProofOutboxJob job = new ProofOutboxJob(service, "worker-1", 10);
        Logger logger = (Logger) LoggerFactory.getLogger(ProofOutboxJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            job.processPendingEvents();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("Proof outbox batch completed");
                    UUID.fromString(
                            event.getMDCPropertyMap().get("worker_run_id")
                    );
                });
    }
}

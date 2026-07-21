package com.naesan.passport.adapter.in.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.application.ProcessProofOutboxService;

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
}

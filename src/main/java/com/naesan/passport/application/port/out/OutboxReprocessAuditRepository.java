package com.naesan.passport.application.port.out;

import com.naesan.passport.domain.OutboxReprocessAudit;

public interface OutboxReprocessAuditRepository {

    void save(OutboxReprocessAudit audit);
}

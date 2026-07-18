package com.naesan.passport.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record IssuePassportRequest(
        @NotNull(message = "Snapshot ID는 필수입니다.")
        UUID snapshotId
) {
}

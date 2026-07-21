package com.naesan.share.adapter.in.web;

import jakarta.validation.constraints.NotNull;

import com.naesan.share.domain.PublicShareCapability;

public record IssuePublicShareRequest(
        @NotNull(message = "공개 범위는 필수입니다.")
        PublicShareCapability capability
) {
}

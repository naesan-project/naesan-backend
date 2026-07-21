package com.naesan.share.adapter.in.web;

public sealed interface PublicVerificationResponse
        permits PublicSummaryVerificationResponse,
        PublicFileMatchVerificationResponse {
}

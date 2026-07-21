package com.naesan.share.application;

public record PublicFileMatchResult(
        boolean matched,
        String trustStage,
        String commitment
) {
}

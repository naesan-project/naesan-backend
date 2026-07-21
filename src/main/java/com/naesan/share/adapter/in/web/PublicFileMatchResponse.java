package com.naesan.share.adapter.in.web;

import com.naesan.share.application.PublicFileMatchResult;

public record PublicFileMatchResponse(
        boolean matched,
        String trustStage,
        String commitment
) {

    public static PublicFileMatchResponse from(PublicFileMatchResult result) {
        return new PublicFileMatchResponse(
                result.matched(),
                result.trustStage(),
                result.commitment()
        );
    }
}

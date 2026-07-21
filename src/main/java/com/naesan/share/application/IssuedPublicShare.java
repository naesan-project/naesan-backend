package com.naesan.share.application;

import com.naesan.share.domain.PublicShare;

public record IssuedPublicShare(
        PublicShare publicShare,
        String rawToken
) {
}

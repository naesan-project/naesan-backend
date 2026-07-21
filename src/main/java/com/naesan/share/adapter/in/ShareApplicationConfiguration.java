package com.naesan.share.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import com.naesan.passport.domain.AnchorCommitmentCalculator;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.share.adapter.out.security.SecurePublicShareTokenCodec;
import com.naesan.share.adapter.in.web.PublicVerificationRateLimitFilter;
import com.naesan.share.application.ManagePublicShareService;
import com.naesan.share.application.PublicFileMatchVerifier;
import com.naesan.share.application.VerifyPublicShareService;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.share.application.port.out.PublicShareTokenCodec;
import com.naesan.share.application.port.out.PublicShareVerificationRepository;

@Configuration(proxyBeanMethods = false)
public class ShareApplicationConfiguration {

    @Bean
    PublicShareTokenCodec publicShareTokenCodec() {
        return new SecurePublicShareTokenCodec();
    }

    @Bean
    ManagePublicShareService managePublicShareService(
            PassportRepository passportRepository,
            PublicShareRepository publicShareRepository,
            PublicShareTokenCodec tokenCodec,
            @Value("${naesan.share.time-to-live}") Duration timeToLive,
            Clock clock
    ) {
        return new ManagePublicShareService(
                passportRepository,
                publicShareRepository,
                tokenCodec,
                timeToLive,
                clock
        );
    }

    @Bean
    VerifyPublicShareService verifyPublicShareService(
            PublicShareTokenCodec tokenCodec,
            PublicShareVerificationRepository verificationRepository,
            PublicFileMatchVerifier fileMatchVerifier,
            Clock clock
    ) {
        return new VerifyPublicShareService(
                tokenCodec,
                verificationRepository,
                fileMatchVerifier,
                clock
        );
    }

    @Bean
    PublicFileMatchVerifier publicFileMatchVerifier(
            AnchorCommitmentCalculator commitmentCalculator,
            @Value("${naesan.share.file-match.max-size}") DataSize maximumFileSize
    ) {
        return new PublicFileMatchVerifier(
                commitmentCalculator,
                maximumFileSize.toBytes()
        );
    }

    @Bean
    PublicVerificationRateLimitFilter publicVerificationRateLimitFilter(
            @Value("${naesan.share.rate-limit.verification-requests}")
            int verificationRequestLimit,
            @Value("${naesan.share.rate-limit.file-match-requests}")
            int fileMatchRequestLimit,
            @Value("${naesan.share.rate-limit.window}") Duration windowDuration,
            Clock clock
    ) {
        return new PublicVerificationRateLimitFilter(
                verificationRequestLimit,
                fileMatchRequestLimit,
                windowDuration,
                clock
        );
    }
}

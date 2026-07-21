package com.naesan.share.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.share.adapter.out.security.SecurePublicShareTokenCodec;
import com.naesan.share.application.ManagePublicShareService;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.share.application.port.out.PublicShareTokenCodec;

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
}

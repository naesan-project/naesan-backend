package com.naesan.transfer.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.transfer.application.CreateTransferRequestService;
import com.naesan.transfer.application.port.out.TransferRequestRepository;

@Configuration(proxyBeanMethods = false)
public class TransferApplicationConfiguration {

    @Bean
    CreateTransferRequestService createTransferRequestService(
            AccountRepository accountRepository,
            PassportRepository passportRepository,
            TransferRequestRepository transferRequestRepository,
            @Value("${naesan.transfer.time-to-live}") Duration timeToLive,
            Clock clock
    ) {
        return new CreateTransferRequestService(
                accountRepository,
                passportRepository,
                transferRequestRepository,
                timeToLive,
                clock
        );
    }
}

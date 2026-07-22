package com.naesan.transfer.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.transfer.application.AcceptTransferRequestService;
import com.naesan.transfer.application.CreateTransferRequestService;
import com.naesan.transfer.application.ListTransferRequestsService;
import com.naesan.transfer.application.ManageTransferRequestService;
import com.naesan.transfer.application.port.out.TransferRequestQueryRepository;
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

    @Bean
    ManageTransferRequestService manageTransferRequestService(
            TransferRequestRepository transferRequestRepository,
            Clock clock
    ) {
        return new ManageTransferRequestService(transferRequestRepository, clock);
    }

    @Bean
    AcceptTransferRequestService acceptTransferRequestService(
            TransferRequestRepository transferRequestRepository,
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository,
            PublicShareRepository publicShareRepository,
            Clock clock
    ) {
        return new AcceptTransferRequestService(
                transferRequestRepository,
                passportRepository,
                ownershipHistoryRepository,
                publicShareRepository,
                clock
        );
    }

    @Bean
    ListTransferRequestsService listTransferRequestsService(
            TransferRequestQueryRepository queryRepository,
            Clock clock
    ) {
        return new ListTransferRequestsService(queryRepository, clock);
    }
}

package com.mariosmant.fintech.infrastructure.config;

import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.application.saga.TransferSagaOrchestrator;
import com.mariosmant.fintech.application.usecase.*;
import com.mariosmant.fintech.domain.port.outbound.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Bean configuration wiring application-layer use cases and the saga orchestrator
 * to their infrastructure-provided port implementations.
 *
 * <p>This is the composition root of the hexagonal architecture — the only place
 * where concrete implementations are bound to abstract ports.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration
public class BeanConfig {

    @Bean
    public InitiateTransferUseCase initiateTransferUseCase(
            TransferRepository transferRepository,
            OutboxRepository outboxRepository,
            AccountRepository accountRepository) {
        return new InitiateTransferUseCase(transferRepository, outboxRepository, accountRepository);
    }

    @Bean
    public TransferQueryUseCase transferQueryUseCase(TransferRepository transferRepository,
                                                     AccountRepository accountRepository) {
        return new TransferQueryUseCase(transferRepository, accountRepository);
    }

    @Bean
    public AccountUseCase accountUseCase(AccountRepository accountRepository) {
        return new AccountUseCase(accountRepository);
    }

    @Bean
    public LedgerQueryUseCase ledgerQueryUseCase(LedgerRepository ledgerRepository,
                                                 AccountRepository accountRepository,
                                                 TransferRepository transferRepository) {
        return new LedgerQueryUseCase(ledgerRepository, accountRepository, transferRepository);
    }

    @Bean
    public TransferSagaOrchestrator transferSagaOrchestrator(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository,
            OutboxRepository outboxRepository,
            FraudDetectionPort fraudDetectionPort,
            FxRatePort fxRatePort) {
        return new TransferSagaOrchestrator(
                transferRepository, accountRepository, ledgerRepository,
                outboxRepository, fraudDetectionPort, fxRatePort);
    }
}


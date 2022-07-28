package com.seltzer.split.domain.service;

import com.azure.cosmos.models.PartitionKey;
import com.seltzer.split.domain.model.FinalState;
import com.seltzer.split.domain.model.Payment;
import com.seltzer.split.domain.model.Transaction;
import com.seltzer.split.domain.repository.FinalStateRepository;
import com.seltzer.split.domain.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TERMINOLOGIES:
 * +==============+
 * 1. "OUTWARD BALANCE" = Amount of money paid (or lent) - Amount of money received.
 * 2. "INWARD BALANCE" =  Amount of money received - Amount of money paid (or lent).
 * 3. "INWARD TRANSACTIONS" = All those transactions where some money has been received.
 * 4. "OUTWARD TRANSACTIONS" = All those transactions where some money has been paid/lent.
 */
@Service
@AllArgsConstructor
public class TransactionHandler {
    private final TransactionRepository transactionRepository;
    private final FinalStateRepository finalStateRepository;

    private String generateID(@NonNull final Transaction transaction) {
        return DigestUtils.sha256Hex(transaction.toString());
    }

    private List<FinalState> getAllFinalStateTransactions(final String userId) {
        return finalStateRepository.findAll(new PartitionKey(userId));
    }


    /**
     * @param userId The ID of the user for whom to derive the balance.
     * @return A <code>Map</code> representing users and their corresponding amount of money that the individual owes to the user with <code>userId</code>.
     * @apiNote Takes a <code>userId</code> and returns a <code>Map</code> representing users and their corresponding amount of money that the individual
     * owes to the user with <code>userId</code>.
     */
    public List<FinalState> getAmountsToBeReceived(@NonNull final String userId) {
        var transactionTips = getAllFinalStateTransactions(userId);
        return transactionTips.stream()
                .filter(state -> state.getBalance() > 0)
                .collect(Collectors.toList());
    }


    /**
     * @param userId The ID of the user for whom to derive the balance.
     * @return A <code>Map</code> representing users and their corresponding amount of money that the individual should be paid by the user with <code>userId</code>.
     * @apiNote Takes a <code>userId</code> and returns a <code>Map</code> representing users and their corresponding amount of money that the individual
     * should be paid by the user with <code>userId</code>.
     */
    public List<FinalState> getAmountsToBePaid(@NonNull final String userId) {
        var transactionTips = getAllFinalStateTransactions(userId);
        return transactionTips.stream()
                .filter(state -> state.getBalance() < 0)
                .map(state -> state.withBalance(Math.abs(state.getBalance())))
                .collect(Collectors.toList());
    }

    /**
     * @param transaction The transaction to record.
     * @apiNote Creates a complement of the given transaction to record both Inward and Outward transactions.
     */
    public void addTransaction(@NonNull final Transaction transaction) {
        if (transaction.getReceivedFrom().isPresent()) {
            final var participantId = transaction.getReceivedFrom().get().getUserId();
            final var finalStateSelfOpt = finalStateRepository.findById(participantId, new PartitionKey(transaction.getUserId()));
            final var finalStateParticipantOpt = finalStateRepository.findById(transaction.getUserId(), new PartitionKey(participantId));

            final var selfFinalState = finalStateSelfOpt.orElseGet(FinalState::new);
            final var participantFinalState = finalStateParticipantOpt.orElseGet(FinalState::new);

            final var receivedAmount = transaction.getReceivedFrom().get().getMoney().getAmount();
            final var updatedSelfFinalState = selfFinalState
                    .withUserId(transaction.getUserId())
                    .withParticipantId(participantId)
                    .withBalance(selfFinalState.getBalance() - receivedAmount)
                    .withCurrency(transaction.getReceivedFrom().get().getMoney().getCurrency());

            final var updatedParticipantFinalState = participantFinalState
                    .withUserId(participantId)
                    .withParticipantId(transaction.getUserId())
                    .withBalance(participantFinalState.getBalance() + receivedAmount)
                    .withCurrency(transaction.getReceivedFrom().get().getMoney().getCurrency());

            final var outTxn = new Transaction()
                    .withId(String.valueOf(OffsetDateTime.now().toInstant().toEpochMilli())) // to add uniqueness to the hash generation.
                    .withUserId(transaction.getReceivedFrom().get().getUserId())
                    .withPaidTo(new Payment()
                            .withUserId(transaction.getUserId())
                            .withMoney(transaction.getReceivedFrom().get().getMoney())
                    );

            transactionRepository.saveAll(List.of(outTxn.withId(generateID(outTxn)), getUpdatedTransaction(transaction)));
            finalStateRepository.saveAll(List.of(updatedSelfFinalState, updatedParticipantFinalState));
        } else if (transaction.getPaidTo().isPresent()) {
            final var participantId = transaction.getPaidTo().get().getUserId();
            final var selfFinalStateOpt = finalStateRepository.findById(participantId, new PartitionKey(transaction.getUserId()));
            final var participantFinalStateOpt = finalStateRepository.findById(transaction.getUserId(), new PartitionKey(participantId));

            final var selfFinalState = selfFinalStateOpt.orElseGet(FinalState::new);
            final var participantFinalState = participantFinalStateOpt.orElseGet(FinalState::new);

            final var payment = transaction.getPaidTo().get();
            final var paidAmount = payment.isSettlement() ? 0 : transaction.getPaidTo().get().getMoney().getAmount();

            final var updatedSelfFinalState = selfFinalState
                    .withUserId(transaction.getUserId())
                    .withParticipantId(participantId)
                    .withBalance(payment.isSettlement() ? 0 : selfFinalState.getBalance() + paidAmount)
                    .withCurrency(transaction.getPaidTo().get().getMoney().getCurrency());

            final var updatedParticipantFinalState = participantFinalState
                    .withParticipantId(transaction.getUserId())
                    .withUserId(participantId)
                    .withBalance(payment.isSettlement() ? 0 : participantFinalState.getBalance() - paidAmount)
                    .withCurrency(transaction.getPaidTo().get().getMoney().getCurrency());

            final var inTxn = new Transaction()
                    .withId(String.valueOf(OffsetDateTime.now().toInstant().toEpochMilli())) // to add uniqueness to the hash generation.
                    .withUserId(transaction.getPaidTo().get().getUserId())
                    .withReceivedFrom(new Payment()
                            .withUserId(transaction.getUserId())
                            .withMoney(payment.isSettlement() ? selfFinalState.getMoney() : transaction.getPaidTo().get().getMoney())
                    );
            if (payment.isSettlement()) {
                var updatedPayment = payment.withMoney(selfFinalState.getMoney());
                transactionRepository.saveAll(List.of(inTxn.withId(generateID(inTxn)), getUpdatedTransaction(transaction.withPaidTo(updatedPayment))));
            } else {
                transactionRepository.saveAll(List.of(inTxn.withId(generateID(inTxn)), getUpdatedTransaction(transaction)));
            }
            finalStateRepository.saveAll(List.of(updatedParticipantFinalState, updatedSelfFinalState));
        } else {
            throw new IllegalArgumentException(String.format("Not a valid transaction %s", transaction));
        }
    }

    private Transaction getUpdatedTransaction(@NonNull final Transaction transaction) {
        var id = generateID(transaction.withId(String.valueOf(OffsetDateTime.now().toInstant().toEpochMilli())));
        return transaction.withId(id);
    }
}

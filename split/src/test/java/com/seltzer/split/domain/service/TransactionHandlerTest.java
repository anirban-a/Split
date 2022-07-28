package com.seltzer.split.domain.service;

import com.azure.cosmos.models.PartitionKey;
import com.seltzer.split.domain.model.Currency;
import com.seltzer.split.domain.model.FinalState;
import com.seltzer.split.domain.model.Payment;
import com.seltzer.split.domain.model.Transaction;
import com.seltzer.split.domain.repository.FinalStateRepository;
import com.seltzer.split.domain.repository.TransactionRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TransactionHandlerTest {
    private final TransactionRepository transactionRepository = Mockito.mock(TransactionRepository.class);
    private final FinalStateRepository finalStateRepository = Mockito.mock(FinalStateRepository.class);
    private final TransactionHandler transactionHandler = new TransactionHandler(transactionRepository, finalStateRepository);

    @Captor
    ArgumentCaptor<List<Transaction>> transactionsCaptor;
    @Captor
    ArgumentCaptor<List<FinalState>> finalStatesCaptor;

    @Test
    public void testAmountsToBeReceived_01() {

        var txnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(100.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");

        var txnState2 = new FinalState()
                .withUserId("user-1")
                .withBalance(200.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-3");

        var mockTxns = List.of(
                txnState1, txnState2
        );

        when(finalStateRepository.findAll(any(PartitionKey.class))).thenReturn(mockTxns);

        var finalAmountsToBeReceived = transactionHandler.getAmountsToBeReceived("user-1");

        assertTrue(finalAmountsToBeReceived.stream().anyMatch(finalState -> finalState.getUserId().equals("user-1")
                && finalState.getBalance() == 100.0f
                && finalState.getParticipantId().equals("user-2")
                && finalState.getCurrency() == Currency.USD));

        assertTrue(finalAmountsToBeReceived.stream().anyMatch(finalState -> finalState.getUserId().equals("user-1")
                && finalState.getBalance() == 200.0f
                && finalState.getParticipantId().equals("user-3")
                && finalState.getCurrency() == Currency.USD));
    }

    @Test
    public void testAmountsToBeReceived_02() {

        var txnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(100.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");


        var txnState2 = new FinalState()
                .withUserId("user-1")
                .withBalance(-80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-3");

        var mockTxns = List.of(
                txnState1, txnState2
        );

        when(finalStateRepository.findAll(any(PartitionKey.class))).thenReturn(mockTxns);

        var finalAmountsToBeReceived = transactionHandler.getAmountsToBeReceived("user-1");
        assertEquals(1, finalAmountsToBeReceived.size());
        assertEquals(100.0f, finalAmountsToBeReceived.get(0).getBalance());
    }


    @Test
    public void testAmountsToBePaid_01() {
        var txnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(100.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");


        var txnState2 = new FinalState()
                .withUserId("user-1")
                .withBalance(-80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-3");

        var mockTxns = List.of(
                txnState1, txnState2
        );

        when(finalStateRepository.findAll(any(PartitionKey.class))).thenReturn(mockTxns);

        var finalAmountsToBeReceived = transactionHandler.getAmountsToBePaid("user-1");
        assertEquals(1, finalAmountsToBeReceived.size());
        assertEquals(80.0f, finalAmountsToBeReceived.get(0).getBalance());
    }

    @Test
    public void testAddTransaction_01() {
        var txn1 = new Transaction()
                .withUserId("user-1")
                .withPaidTo(new Payment()
                        .withUserId("user-2")
                        .withMoney(new Payment.Money(100.0f, Currency.USD))
                );

        var expectedTxn1Complement = new Transaction()
                .withUserId("user-2")
                .withReceivedFrom(new Payment()
                        .withUserId("user-1")
                        .withMoney(new Payment.Money(100.0f, Currency.USD))
                );

        var existingTxnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");


        var existingTxnState2 = new FinalState()
                .withUserId("user-2")
                .withBalance(-80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-1");

        when(finalStateRepository.findById("user-2", new PartitionKey("user-1"))).thenReturn(Optional.of(existingTxnState1));
        when(finalStateRepository.findById("user-1", new PartitionKey("user-2"))).thenReturn(Optional.of(existingTxnState2));

        when(transactionRepository.saveAll(transactionsCaptor.capture())).thenReturn(List.of());
        when(finalStateRepository.saveAll(finalStatesCaptor.capture())).thenReturn(null);
        transactionHandler.addTransaction(txn1);

        var actualTxns = transactionsCaptor.getValue();
        var actualFinalStates = finalStatesCaptor.getValue();

        assertEquals(2, actualFinalStates.size());
        var updatedParticipantFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-2")).findFirst();
        var updatedSelfFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-1")).findFirst();

        assertTrue(updatedParticipantFinalStateOpt.isPresent());
        assertTrue(updatedSelfFinalStateOpt.isPresent());

        assertEquals(180.0f, updatedSelfFinalStateOpt.get().getBalance());
        assertEquals(-180.0f, updatedParticipantFinalStateOpt.get().getBalance());

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(txn1.getUserId())
                                && txn.getPaidTo().isPresent()
                                && txn.getPaidTo().get().getUserId().equals(txn1.getPaidTo().get().getUserId())
                                && txn.getPaidTo().get().getMoney().getAmount() == 100.0f
                ));

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(expectedTxn1Complement.getUserId())
                                && txn.getReceivedFrom().isPresent()
                                && txn.getReceivedFrom().get().getUserId().equals(expectedTxn1Complement.getReceivedFrom().get().getUserId())
                                && txn.getReceivedFrom().get().getMoney().getAmount() == 100.0f
                ));
    }

    @Test
    public void testAddTransaction_02() {
        var txn1 = new Transaction()
                .withUserId("user-2")
                .withReceivedFrom(new Payment()
                        .withUserId("user-1")
                        .withMoney(new Payment.Money(100.0f, Currency.USD))
                );

        var expectedTxn1Complement = new Transaction()
                .withUserId("user-1")
                .withPaidTo(new Payment()
                        .withUserId("user-2")
                        .withMoney(new Payment.Money(100.0f, Currency.USD))
                );

        var existingTxnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(-80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");


        var existingTxnState2 = new FinalState()
                .withUserId("user-2")
                .withBalance(80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-1");

        when(finalStateRepository.findById("user-2", new PartitionKey("user-1"))).thenReturn(Optional.of(existingTxnState1));
        when(finalStateRepository.findById("user-1", new PartitionKey("user-2"))).thenReturn(Optional.of(existingTxnState2));

        when(transactionRepository.saveAll(transactionsCaptor.capture())).thenReturn(List.of());
        when(finalStateRepository.saveAll(finalStatesCaptor.capture())).thenReturn(null);

        transactionHandler.addTransaction(txn1);

        var actualTxns = transactionsCaptor.getValue();
        var actualFinalStates = finalStatesCaptor.getValue();

        assertEquals(2, actualFinalStates.size());
        var updatedParticipantFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-1")).findFirst();
        var updatedSelfFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-2")).findFirst();

        assertTrue(updatedParticipantFinalStateOpt.isPresent());
        assertTrue(updatedSelfFinalStateOpt.isPresent());

        assertEquals(-20.0f, updatedSelfFinalStateOpt.get().getBalance());
        assertEquals(20.0f, updatedParticipantFinalStateOpt.get().getBalance());

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(expectedTxn1Complement.getUserId())
                                && txn.getPaidTo().isPresent()
                                && txn.getPaidTo().get().getUserId().equals(expectedTxn1Complement.getPaidTo().get().getUserId())
                                && txn.getPaidTo().get().getMoney().getAmount() == 100.0f
                ));

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(txn1.getUserId())
                                && txn.getReceivedFrom().isPresent()
                                && txn.getReceivedFrom().get().getUserId().equals(txn1.getReceivedFrom().get().getUserId())
                                && txn.getReceivedFrom().get().getMoney().getAmount() == 100.0f
                ));
    }

    @Test
    public void testAddTransaction_03() {
        var txn1 = new Transaction()
                .withUserId("user-2");

        assertThrows(IllegalArgumentException.class, () -> transactionHandler.addTransaction(txn1));
    }

    @Test
    public void testAddTransaction_04() {
        var txn1 = new Transaction()
                .withUserId("user-1")
                .withPaidTo(new Payment()
                        .withUserId("user-2")
                        .withMoney(new Payment.Money(0, Currency.USD))
                        .withSettlement(true));

        var expectedTxn1Complement = new Transaction()
                .withUserId("user-2")
                .withReceivedFrom(new Payment()
                        .withUserId("user-1")
                        .withMoney(new Payment.Money(80.0f, Currency.USD))
                );

        var existingTxnState1 = new FinalState()
                .withUserId("user-1")
                .withBalance(80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-2");


        var existingTxnState2 = new FinalState()
                .withUserId("user-2")
                .withBalance(-80.0f)
                .withCurrency(Currency.USD)
                .withParticipantId("user-1");

        when(finalStateRepository.findById("user-2", new PartitionKey("user-1"))).thenReturn(Optional.of(existingTxnState1));
        when(finalStateRepository.findById("user-1", new PartitionKey("user-2"))).thenReturn(Optional.of(existingTxnState2));

        when(transactionRepository.saveAll(transactionsCaptor.capture())).thenReturn(List.of());
        when(finalStateRepository.saveAll(finalStatesCaptor.capture())).thenReturn(null);
        transactionHandler.addTransaction(txn1);

        var actualTxns = transactionsCaptor.getValue();
        var actualFinalStates = finalStatesCaptor.getValue();

        assertEquals(2, actualFinalStates.size());
        var updatedParticipantFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-2")).findFirst();
        var updatedSelfFinalStateOpt = actualFinalStates.stream().filter(finalState -> finalState.getUserId().equals("user-1")).findFirst();

        assertTrue(updatedParticipantFinalStateOpt.isPresent());
        assertTrue(updatedSelfFinalStateOpt.isPresent());

        assertEquals(0.0f, updatedSelfFinalStateOpt.get().getBalance());
        assertEquals(0.0f, updatedParticipantFinalStateOpt.get().getBalance());

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(txn1.getUserId())
                                && txn.getPaidTo().isPresent()
                                && txn.getPaidTo().get().getUserId().equals(txn1.getPaidTo().get().getUserId())
                                && txn.getPaidTo().get().getMoney().getAmount() == 80.0f
                ));

        assertTrue(actualTxns.stream()
                .anyMatch(txn ->
                        txn.getUserId().equals(expectedTxn1Complement.getUserId())
                                && txn.getReceivedFrom().isPresent()
                                && txn.getReceivedFrom().get().getUserId().equals(expectedTxn1Complement.getReceivedFrom().get().getUserId())
                                && txn.getReceivedFrom().get().getMoney().getAmount() == 80.0f
                ));
    }
}

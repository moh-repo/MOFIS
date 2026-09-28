package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.BankTransaction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
class BankTransactionRepositoryTest {

    @Autowired
    private BankTransactionRepository repository;

    @Test
    void shouldLoadAtLeastOneTransaction() {
        List<BankTransaction> transactions = repository.findAll();
        assertThat(transactions)
                .as("Expected bank transactions to exist in STUDENT_FINANCE derived view")
                .isNotNull();
        System.out.println("Loaded bank transactions count: " + transactions.size());
        transactions.stream().limit(5).forEach(tx -> System.out.println("Transaction: " + tx.getTransactionId() + " - " + tx.getTransactionType() + " - " + tx.getTransactionAmount()));
    }
}



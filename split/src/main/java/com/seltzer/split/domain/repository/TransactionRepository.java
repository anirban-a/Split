package com.seltzer.split.domain.repository;

import com.azure.cosmos.models.PartitionKey;
import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.seltzer.split.domain.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends CosmosRepository<Transaction, String> {
    List<Transaction> findAll(PartitionKey partitionKey);
}

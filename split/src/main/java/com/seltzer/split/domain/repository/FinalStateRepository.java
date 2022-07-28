package com.seltzer.split.domain.repository;

import com.azure.cosmos.models.PartitionKey;
import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.seltzer.split.domain.model.FinalState;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinalStateRepository extends CosmosRepository<FinalState, String> {
    List<FinalState> findAll(PartitionKey partitionKey);
}

package com.seltzer.split.domain.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Optional;


@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
@Container(containerName = "transaction")
public class Transaction {
    @PartitionKey
    String partitionKey;
    @Getter
    @With
    String userId;
    @Getter
    @With
    String id;
    @With
    Payment paidTo;
    @With
    Payment receivedFrom;

    public Optional<Payment> getPaidTo() {
        return Optional.ofNullable(paidTo);
    }

    public Optional<Payment> getReceivedFrom() {
        return Optional.ofNullable(receivedFrom);
    }

    public void setPartitionKey(){
        if(paidTo == null){
            this.partitionKey = String.format("%s_%s", this.userId, receivedFrom.getUserId());
        }else {
            this.partitionKey = String.format("%s_%s", this.userId, paidTo.getUserId());
        }
    }
}

package com.seltzer.split.domain.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;

@With
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
@Container(containerName = "final-state")
@Getter
public class FinalState {

    @PartitionKey
    String userId;
    @Id
    String participantId;
    float balance; // can be -ve, 0, +ve
    Currency currency;

    public Payment.Money getMoney() {
        return new Payment.Money(Math.abs(balance), currency);
    }
}

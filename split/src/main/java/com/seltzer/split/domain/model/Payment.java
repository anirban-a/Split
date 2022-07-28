package com.seltzer.split.domain.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@With
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@ToString
public class Payment {

    String userId;
    Money money;
    boolean settlement;
    @With
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @ToString
    public static final class Money {
        float amount;
        Currency currency;
    }
}

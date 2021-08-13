package com.market.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class OrderResponse {
    private String locationLatitide;
    private String locationLongitude;
    private Long userId;
    private Set<Long> items;
}

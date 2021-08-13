package com.market.service;

import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderResponse;

public interface OrderService {
    OrderResponse placeOrder(OrderRequest orderRequest);
}

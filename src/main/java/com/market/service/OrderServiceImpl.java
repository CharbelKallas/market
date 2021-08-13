package com.market.service;

import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceImpl implements OrderService {

    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        return null;
    }

}

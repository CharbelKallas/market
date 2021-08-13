package com.market.controller;

import com.market.payload.Response;
import com.market.payload.request.OrderRequest;
import com.market.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    OrderService orderService;

    @PostMapping("/place")
    public Response<?> placeOrder(@RequestBody @Valid OrderRequest request) {
        return Response.ok().setPayload(orderService.placeOrder(request));
    }


}

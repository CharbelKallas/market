package com.market.controller;

import com.market.payload.Response;
import com.market.payload.request.OrderRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @PostMapping("/place")
    public Response<?> placeOrder(@RequestBody @Valid OrderRequest request) {
        return Response.ok().setPayload("");
    }


}

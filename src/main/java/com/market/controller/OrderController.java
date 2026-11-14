package com.market.controller;

import com.market.payload.Response;
import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderResponse;
import com.market.security.services.UserDetailsImpl;
import com.market.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Places an order for the signed-in user. */
    @PostMapping("/place")
    @PreAuthorize("hasRole('USER')")
    public Response<OrderResponse> placeOrder(@AuthenticationPrincipal UserDetailsImpl user,
                                              @RequestBody @Valid OrderRequest request) {
        return Response.<OrderResponse>ok().setPayload(orderService.placeOrder(user.getId(), request));
    }

}

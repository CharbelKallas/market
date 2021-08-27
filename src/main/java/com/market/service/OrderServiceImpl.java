package com.market.service;

import com.market.exception.MarketException;
import com.market.model.order.Item;
import com.market.model.order.Order;
import com.market.model.order.OrderItem;
import com.market.model.user.User;
import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderResponse;
import com.market.repository.user.ItemRepository;
import com.market.repository.user.OrderRepository;
import com.market.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.market.exception.EntityType.USER;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Component
public class OrderServiceImpl implements OrderService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        User user = userRepository.findById(orderRequest.getUserId()).orElseThrow(() -> MarketException.throwException(USER, ENTITY_NOT_FOUND, orderRequest.getUserId().toString()));
        List<Item> items = itemRepository.findAllById(orderRequest.getItems());

        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order()
                .setUser(user)
                .setOrderDate(new Date())
                .setLocationLatitide(orderRequest.getLocationLatitide())
                .setLocationLongitude(orderRequest.getLocationLongitude())
                .setDeliveryCharge(2d);

        items.forEach(item -> orderItems.add(new OrderItem().setItem(item).setAmount(item.getAmount()).setOrder(order)));

        order.setOrderItems(new HashSet<>(orderItems));

        return toOrderResponse(orderRepository.save(order));
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse()
                .setUserId(order.getUser().getId())
                .setLocationLatitide(order.getLocationLatitide())
                .setLocationLongitude(order.getLocationLongitude())
                .setItems(new HashSet<>(order
                        .getOrderItems()
                        .stream()
                        .map(OrderItem::getId)
                        .collect(Collectors.toSet())));
    }


}

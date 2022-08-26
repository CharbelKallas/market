package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.model.order.Order;
import com.market.model.order.OrderItem;
import com.market.model.user.User;
import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderItemResponse;
import com.market.payload.response.OrderResponse;
import com.market.repository.ItemRepository;
import com.market.repository.OrderRepository;
import com.market.repository.UserRepository;
import com.market.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;

    private final ItemRepository itemRepository;

    private final OrderRepository orderRepository;

    @Autowired
    public OrderServiceImpl(UserRepository userRepository, ItemRepository itemRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        User user = userRepository.findById(orderRequest.getUserId())
                .orElseThrow(() -> MarketException.throwException("User - " + orderRequest.getUserId() + " does not exist."));

        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order()
                .setUser(user)
                .setOrderDate(new Date())
                .setLocationLatitide(orderRequest.getLocationLatitide())
                .setLocationLongitude(orderRequest.getLocationLongitude())
                .setDeliveryCharge(2d);

        orderRequest.getItems().forEach((itemId, qty) -> {
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> MarketException.throwException("item " + itemId + " does not found."));

            Stream<ItemAmount> amountStream = item.getItemAmounts().stream().filter(itemAmount -> itemAmount.getActiveDate().isBefore(LocalDateTime.now()));
            ItemAmount itemAmount = null;
            for (Object o : amountStream.toArray())
                if (itemAmount == null)
                    itemAmount = (ItemAmount) o;
                else if (((ItemAmount) o).getActiveDate().isAfter(itemAmount.getActiveDate()))
                    itemAmount = (ItemAmount) o; // TODO: 8/26/22

            if (itemAmount == null)
                throw MarketException.throwException("Amount does not found for Item " + item.getItemName() + ".");

            orderItems.add(new OrderItem().setItemAmount(itemAmount).setItemQty(qty).setOrder(order));
        });

        order.setOrderItems(new HashSet<>(orderItems));

        return toOrderResponse(orderRepository.save(order));
    }

    private OrderResponse toOrderResponse(Order order) {

        Set<OrderItemResponse> orderItems = new HashSet<>();
        order.getOrderItems().forEach(orderItem -> orderItems.add(new OrderItemResponse()
                .setItemId(orderItem.getItemAmount().getItem().getId())
                .setAmount(orderItem.getItemAmount().getAmount() * orderItem.getItemQty())
                .setItemQty(orderItem.getItemQty())));

        return new OrderResponse()
                .setUserId(order.getUser().getId())
                .setLocationLatitide(order.getLocationLatitide())
                .setLocationLongitude(order.getLocationLongitude())
                .setOrderItems(orderItems);
    }


}

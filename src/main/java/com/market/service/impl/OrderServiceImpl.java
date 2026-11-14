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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;

    private final ItemRepository itemRepository;

    private final OrderRepository orderRepository;

    private final BigDecimal deliveryCharge;

    public OrderServiceImpl(UserRepository userRepository, ItemRepository itemRepository, OrderRepository orderRepository,
                            @Value("${app.order.deliveryCharge}") BigDecimal deliveryCharge) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.deliveryCharge = deliveryCharge;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest orderRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MarketException.notFound("User - " + userId + " does not exist."));

        Order order = new Order()
                .setUser(user)
                .setOrderDate(new Date())
                .setLocationLatitude(orderRequest.getLocationLatitude())
                .setLocationLongitude(orderRequest.getLocationLongitude())
                .setDeliveryCharge(deliveryCharge);

        Map<Long, Item> items = itemRepository.findAllById(orderRequest.getItems().keySet()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Set<Long> missing = new TreeSet<>(orderRequest.getItems().keySet());
        missing.removeAll(items.keySet());
        if (!missing.isEmpty())
            throw MarketException.notFound("Items not found: " + missing);

        orderRequest.getItems().forEach((itemId, qty) -> {
            Item item = items.get(itemId);

            ItemAmount itemAmount = item.getCurrentAmount()
                    .orElseThrow(() -> MarketException.badRequest("Item " + item.getItemName() + " has no price yet."));

            order.getOrderItems().add(new OrderItem().setItemAmount(itemAmount).setItemQty(qty).setOrder(order));
        });

        return toOrderResponse(orderRepository.save(order));
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> orderItems = order.getOrderItems().stream()
                .map(orderItem -> new OrderItemResponse()
                        .setItemId(orderItem.getItemAmount().getItem().getId())
                        .setItemName(orderItem.getItemAmount().getItem().getItemName())
                        .setItemQty(orderItem.getItemQty())
                        .setUnitPrice(orderItem.getItemAmount().getAmount())
                        .setAmount(orderItem.getLineTotal()))
                .sorted(Comparator.comparing(OrderItemResponse::getItemId))
                .collect(Collectors.toList());

        BigDecimal itemsTotal = order.getOrderItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponse()
                .setId(order.getId())
                .setUserId(order.getUser().getId())
                .setOrderDate(order.getOrderDate())
                .setLocationLatitude(order.getLocationLatitude())
                .setLocationLongitude(order.getLocationLongitude())
                .setOrderItems(orderItems)
                .setDeliveryCharge(order.getDeliveryCharge())
                .setTotal(itemsTotal.add(order.getDeliveryCharge()));
    }

}

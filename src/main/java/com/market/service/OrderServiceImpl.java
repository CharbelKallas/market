package com.market.service;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.model.order.Order;
import com.market.model.order.OrderItem;
import com.market.model.user.User;
import com.market.payload.request.OrderRequest;
import com.market.payload.response.OrderItemResponse;
import com.market.payload.response.OrderResponse;
import com.market.repository.ItemAmountRepository;
import com.market.repository.ItemRepository;
import com.market.repository.OrderRepository;
import com.market.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static com.market.exception.EntityType.*;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Component
public class OrderServiceImpl implements OrderService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemAmountRepository itemAmountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        User user = userRepository.findById(orderRequest.getUserId()).orElseThrow(() -> MarketException.throwException(USER, ENTITY_NOT_FOUND, orderRequest.getUserId().toString()));

        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order()
                .setUser(user)
                .setOrderDate(new Date())
                .setLocationLatitide(orderRequest.getLocationLatitide())
                .setLocationLongitude(orderRequest.getLocationLongitude())
                .setDeliveryCharge(2d);

        orderRequest.getItems().forEach((itemId, qty) -> {
            Item item = itemRepository.findById(itemId).orElseThrow(() -> MarketException.throwException(ITEM, ENTITY_NOT_FOUND, itemId.toString()));

            Stream<ItemAmount> amountStream = item.getItemAmounts().stream().filter(itemAmount -> itemAmount.getActiveDate().isBefore(LocalDateTime.now()));
            ItemAmount itemAmount = null;
            for (Object o : amountStream.toArray())
                if (itemAmount == null)
                    itemAmount = (ItemAmount) o;
                else if (((ItemAmount) o).getActiveDate().isAfter(itemAmount.getActiveDate()))
                    itemAmount = (ItemAmount) o;

            if (itemAmount == null)
                throw MarketException.throwException(ITEM_AMOUNT, ENTITY_NOT_FOUND, item.getItemName());

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

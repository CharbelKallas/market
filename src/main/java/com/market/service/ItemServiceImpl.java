package com.market.service;

import com.market.exception.EntityType;
import com.market.exception.ExceptionType;
import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import com.market.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.market.exception.EntityType.ITEM_AMOUNT;
import static com.market.exception.ExceptionType.ENTITY_NOT_FOUND;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ItemRepository itemRepository;

    @Override
    public ItemResponse getById(NewItemRequest itemRequest) {

        if (itemRepository.existsByItemName(itemRequest.getItemName()))
            throw MarketException.throwException(EntityType.ITEM, ExceptionType.DUPLICATE_ENTITY, itemRequest.getItemName());

        Item item = new Item().setItemName(itemRequest.getItemName());
        item.setItemAmounts(Collections.singleton(new ItemAmount().setItem(item).setAmount(itemRequest.getAmount())));

        return toItemResponse(itemRepository.save(item));

    }

    @Override
    public List<ItemResponse> getAll(int page, int size) {
        return itemRepository.findAll(PageRequest.of(page, size)).get()
                .map(item -> new ItemResponse().setItemName(item.getItemName()).setAmount(getItemAmount(item)))
                .collect(Collectors.toList());
    }

    private ItemResponse toItemResponse(Item item) {
        return new ItemResponse()
                .setItemName(item.getItemName())
                .setAmount(getItemAmount(item));
    }

    private double getItemAmount(Item item) {
        Stream<ItemAmount> amountStream = item.getItemAmounts().stream().filter(itemAmount -> itemAmount.getActiveDate().isBefore(LocalDateTime.now()));
        ItemAmount itemAmount = null;
        for (Object o : amountStream.toArray())
            if (itemAmount == null)
                itemAmount = (ItemAmount) o;
            else if (((ItemAmount) o).getActiveDate().isAfter(itemAmount.getActiveDate()))
                itemAmount = (ItemAmount) o;

        if (itemAmount == null)
            throw MarketException.throwException(ITEM_AMOUNT, ENTITY_NOT_FOUND, item.getItemName());

        return itemAmount.getAmount();
    }

}

package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import com.market.repository.ItemRepository;
import com.market.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Stream;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;

    @Autowired
    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public ItemResponse save(NewItemRequest itemRequest) throws IOException {

        if (itemRepository.existsByItemName(itemRequest.getItemName()))
            throw MarketException.throwException("Item - " + itemRequest.getItemName() + " already exists.");

        Item item = new Item()
                .setItemName(itemRequest.getItemName())
                .setImage(itemRequest.getImage().getBytes())
                .setContentType(itemRequest.getImage().getContentType())
                .setImageName(itemRequest.getImage().getOriginalFilename());
        item.setItemAmounts(Collections.singleton(new ItemAmount().setItem(item).setAmount(itemRequest.getAmount())));

        return toItemResponse(itemRepository.save(item));

    }

    @Override
    public Page<ItemResponse> getAll(Pageable pageable) {
        return itemRepository.findAll(pageable)
                .map(item -> new ItemResponse().setItemName(item.getItemName()).setAmount(getItemAmount(item)));
    }

    @Override
    public Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> MarketException.throwException("item " + itemId + " does not found."));
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
                itemAmount = (ItemAmount) o; // TODO: 8/26/22

        if (itemAmount == null)
            throw MarketException.throwException("Amount does not found for Item " + item.getItemName() + ".");

        return itemAmount.getAmount();
    }

}

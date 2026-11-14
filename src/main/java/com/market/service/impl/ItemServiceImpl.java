package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import com.market.repository.ItemRepository;
import com.market.service.ItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;

    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public ItemResponse save(NewItemRequest itemRequest) {
        String itemName = itemRequest.getItemName().trim();
        if (itemRepository.existsByItemName(itemName))
            throw MarketException.duplicate("Item - " + itemName + " already exists.");

        Item item = new Item().setItemName(itemName);

        MultipartFile image = itemRequest.getImage();
        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/"))
                throw MarketException.badRequest("The uploaded file must be an image.");
            try {
                item.setImage(image.getBytes())
                        .setContentType(contentType)
                        .setImageName(image.getOriginalFilename());
            } catch (IOException e) {
                throw MarketException.internal("Could not read the uploaded image.", e);
            }
        }

        item.getItemAmounts().add(new ItemAmount().setItem(item).setAmount(itemRequest.getAmount()));

        return toItemResponse(itemRepository.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getAll(Pageable pageable) {
        return itemRepository.findAll(pageable).map(this::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> MarketException.notFound("Item " + itemId + " not found."));
    }

    private ItemResponse toItemResponse(Item item) {
        return new ItemResponse()
                .setId(item.getId())
                .setItemName(item.getItemName())
                .setAmount(item.getCurrentAmount().map(ItemAmount::getAmount).orElse(null))
                .setHasImage(item.hasImage());
    }

}

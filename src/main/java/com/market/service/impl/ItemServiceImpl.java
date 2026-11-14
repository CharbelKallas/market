package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.model.item.ItemAmount;
import com.market.model.item.ItemImage;
import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import com.market.repository.ItemRepository;
import com.market.service.ItemService;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;

    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public ItemResponse save(NewItemRequest itemRequest) {
        // "Apple", " apple" and "APPLE" are the same item; inner runs of spaces collapse to one.
        String itemName = itemRequest.getItemName().trim().replaceAll("\\s+", " ");
        if (itemRepository.existsByItemKey(itemName.toLowerCase(Locale.ROOT)))
            throw MarketException.duplicate("Item - " + itemName + " already exists.");

        Item item = new Item().setItemName(itemName);

        MultipartFile image = itemRequest.getImage();
        if (image != null && !image.isEmpty()) {
            byte[] data;
            try {
                data = image.getBytes();
            } catch (IOException e) {
                throw MarketException.internal("Could not read the uploaded image.", e);
            }
            // The type comes from the file's own bytes, never from the client, and only raster formats are allowed
            // (an SVG could carry script).
            String contentType = detectRasterImageType(data);
            if (contentType == null)
                throw MarketException.badRequest("The image must be a PNG, JPEG, GIF or WebP file.");
            item.setImage(new ItemImage()
                    .setData(data)
                    .setContentType(contentType)
                    .setImageName(image.getOriginalFilename()));
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
    public ItemImage getImage(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> MarketException.notFound("Item " + itemId + " not found."));
        if (!item.hasImage())
            throw MarketException.notFound("Item " + itemId + " has no image.");
        return (ItemImage) Hibernate.unproxy(item.getImage());
    }

    static String detectRasterImageType(byte[] d) {
        if (startsWith(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))
            return "image/png";
        if (startsWith(d, 0xFF, 0xD8, 0xFF))
            return "image/jpeg";
        if (startsWith(d, 'G', 'I', 'F', '8'))
            return "image/gif";
        if (startsWith(d, 'R', 'I', 'F', 'F') && d.length >= 12 && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P')
            return "image/webp";
        return null;
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length)
            return false;
        for (int i = 0; i < prefix.length; i++)
            if ((data[i] & 0xFF) != prefix[i])
                return false;
        return true;
    }

    private ItemResponse toItemResponse(Item item) {
        return new ItemResponse()
                .setId(item.getId())
                .setItemName(item.getItemName())
                .setAmount(item.getCurrentAmount().map(ItemAmount::getAmount).orElse(null))
                .setHasImage(item.hasImage());
    }

}

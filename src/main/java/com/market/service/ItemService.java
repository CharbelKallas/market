package com.market.service;

import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemService {
    ItemResponse save(NewItemRequest itemRequest);

    Page<ItemResponse> getAll(Pageable pageable);
}

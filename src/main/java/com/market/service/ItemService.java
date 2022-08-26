package com.market.service;

import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;

public interface ItemService {
    ItemResponse save(NewItemRequest itemRequest) throws IOException;

    Page<ItemResponse> getAll(Pageable pageable);
}

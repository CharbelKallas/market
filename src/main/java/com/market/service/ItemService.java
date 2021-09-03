package com.market.service;

import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;

import java.util.List;

public interface ItemService {
    ItemResponse getById(NewItemRequest itemRequest);

    List<ItemResponse> getAll(int page, int size);
}

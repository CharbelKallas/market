package com.market.controller;

import com.market.exception.MarketException;
import com.market.model.item.Item;
import com.market.payload.Response;
import com.market.payload.request.NewItemRequest;
import com.market.payload.response.ItemResponse;
import com.market.service.ItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@RequestMapping("/api/item")
@Validated
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Response<Page<ItemResponse>> getAll(@RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Response.<Page<ItemResponse>>ok()
                .setPayload(itemService.getAll(PageRequest.of(page, size, Sort.by("itemName"))));
    }

    @PostMapping(value = "/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Response<ItemResponse> save(@ModelAttribute @Valid NewItemRequest request) {
        return Response.<ItemResponse>ok().setPayload(itemService.save(request));
    }

    @GetMapping("/image")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<byte[]> getImage(@RequestParam Long itemId) {
        Item item = itemService.getItem(itemId);
        if (!item.hasImage())
            throw MarketException.notFound("Item " + itemId + " has no image.");

        String filename = item.getImageName() != null ? item.getImageName() : "item-" + itemId;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(item.getContentType()))
                .body(item.getImage());
    }

}

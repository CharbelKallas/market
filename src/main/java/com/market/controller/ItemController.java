package com.market.controller;

import com.market.model.item.Item;
import com.market.payload.Response;
import com.market.payload.request.NewItemRequest;
import com.market.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/item")
public class ItemController {

    private final ItemService itemService;

    @Autowired
    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('USER')")
    public Response<Object> getAll(@RequestParam int page, @RequestParam int size) {
        return Response.ok().setPayload(itemService.getAll(PageRequest.of(page, size)));
    }

    @PostMapping(value = "/new", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasRole('ADMIN')")
    public Response<Object> save(@ModelAttribute @Valid NewItemRequest request) throws IOException {
        return Response.ok().setPayload(itemService.save(request));
    }

    @GetMapping(value = "/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getImage(@RequestParam Long itemId) {
        Item item = itemService.getItem(itemId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + item.getImageName() + "\"")
                .contentType(MediaType.valueOf(item.getContentType()))
                .body(item.getImage());
    }

}

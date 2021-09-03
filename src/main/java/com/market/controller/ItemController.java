package com.market.controller;

import com.market.payload.Response;
import com.market.payload.request.NewItemRequest;
import com.market.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/item")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping("/all")
    @PreAuthorize("hasRole('USER')")
    public Response<?> getAll(@RequestParam int page, @RequestParam int size) {
        return Response.ok().setPayload(itemService.getAll(PageRequest.of(page, size)));
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public Response<?> save(@RequestBody @Valid NewItemRequest request) {
        return Response.ok().setPayload(itemService.save(request));
    }

}

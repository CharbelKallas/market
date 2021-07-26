package com.market.controller;

import com.market.payload.request.UserDto;
import com.market.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {
    @Autowired
    private UserService service;

    @GetMapping
    public List<UserDto> getUsers() {
        return service.findAll();
    }

}

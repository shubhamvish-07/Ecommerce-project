package com.ecommerce.ecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test")
    public String test() {
        return "JWT Authentication Working";
    }

    @GetMapping("/user/test")
    public String userTest() {
        return "USER access working";
    }

}
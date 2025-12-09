package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/test" , "/test"})
public class TestController {
    public String test() {
        return "Test successful!!!";
    }
}

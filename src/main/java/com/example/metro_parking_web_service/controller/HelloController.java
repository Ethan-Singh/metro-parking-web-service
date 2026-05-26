/* (C)2026 */
package com.example.metro_parking_web_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Greetings from Spring Boot!";
    }
}

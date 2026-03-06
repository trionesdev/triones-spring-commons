package com.trionesdev.security.demo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/user")
@RestController
public class UserController {

    @PreAuthorize("hasAnyAuthority('say')")
    @RequestMapping("/hello")
    public String hello() {
        return "hello";
    }
}

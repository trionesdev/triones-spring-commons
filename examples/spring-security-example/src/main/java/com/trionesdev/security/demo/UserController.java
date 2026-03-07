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

    @RequestMapping("/hello2")
    public String hello2() {
        return "hello2";
    }

    @PreAuthorize("hasAnyAuthority('say3')")
    @RequestMapping("/hello3")
    public String hello3() {
        return "hello3";
    }
}

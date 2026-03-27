package com.chua.starter.sync.data.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径显式转发到静态首页。
 */
@Controller
public class HomeController {

    @GetMapping({"/", "/login", "/sync", "/sync/**"})
    public String index() {
        return "forward:/index.html";
    }
}

package com.chua.starter.strategy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Strategy 控制台静态页入口重定向。
 */
@Controller
public class StrategyConsoleController {

    @GetMapping({"/strategy-console", "/strategy-console/"})
    public String index() {
        return "redirect:/strategy-console/index.html";
    }

    @GetMapping({"/strategy-console/login", "/strategy-console/login/"})
    public String login() {
        return "redirect:/strategy-console/login.html";
    }
}

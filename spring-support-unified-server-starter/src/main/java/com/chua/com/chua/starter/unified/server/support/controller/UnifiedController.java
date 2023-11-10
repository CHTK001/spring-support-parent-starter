package com.chua.com.chua.starter.unified.server.support.controller;

import com.chua.com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author CH
 */
@RestController
@RequestMapping("/")
public class UnifiedController {


    @Resource
    private UnifiedServerProperties unifiedServerProperties;
}

package com.chua.starter.mail.support.controller;

import com.chua.starter.mail.support.properties.EmailProperties;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * rest
 * @author CH
 */
@RestController
public class EmailController {

    @Resource
    public EmailProperties emailProperties;


}

package com.chua.starter.server.service.impl;

import com.chua.starter.server.service.DemoService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author CH
 */
@Service
public class DemoServiceImpl implements DemoService {
    @Override
    public String test() {
        return UUID.randomUUID().toString();
    }
}

package com.chua.starter.oauth.server.support.log;

import com.chua.starter.oauth.client.support.annotation.Extension;
import com.chua.starter.oauth.server.support.service.LoggerService;
import lombok.extern.slf4j.Slf4j;

/**
 * log
 *
 * @author CH
 */
@Slf4j
@Extension("simple")
public class SimpleLogServiceImpl implements LoggerService {

    @Override
    public void register(String module, String code, String message, String address) {
        log.info("==============={}==================", address);
        log.info("模块:{}, 状态码: {}", module, code);
        log.info("鉴权消息: {}", message);
        log.info("=================================");
    }
}

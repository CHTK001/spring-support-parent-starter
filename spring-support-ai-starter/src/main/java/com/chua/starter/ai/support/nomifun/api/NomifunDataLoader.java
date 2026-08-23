package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动加载器（预留，数据由用户通过前端界面创建）
 */
@Slf4j
@Component
@Order(1)
public class NomifunDataLoader implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("NomifunDataLoader ready - providers are managed via API/UI, no hardcoded data.");
    }
}

package com.chua.starter.ai.support.manage.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
* 应用启动加载器。
*
* <p>数据由用户通过前端界面创建，启动时不加载任何预置数据。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@Order(1)
public class AppDataLoader implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("应用数据加载器就绪 - 供应商数据通过前端界面管理");
    }
}

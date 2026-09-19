package com.chua.starter.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用冒烟测试：验证聚合各模块自动装配后上下文可正常启动。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
class SpringSupportTestApplicationTests {

    @Test
    /**
     * 上下文加载。
     */
    void contextLoads() {
        // 上下文启动成功即通过（各模块自动装配链无冲突）
    }
}

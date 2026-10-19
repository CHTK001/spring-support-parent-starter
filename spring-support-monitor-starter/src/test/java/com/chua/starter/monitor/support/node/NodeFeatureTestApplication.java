package com.chua.starter.monitor.support.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线节点特性验收用的最小启动器。
 *
 * <p>只装配 node 包（注册表 / 控制器 / sync 处理器）与 sync 服务端，
 * 不加载 monitor 的其它模块，因此不需要数据库即可运行；
 * 用于在浏览器里对「节点管理 → 在线节点」做真实后端验收。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(
        scanBasePackages = "com.chua.starter.monitor.support.node",
        excludeName = {
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        }
)
public class NodeFeatureTestApplication {

    /**
     * 启动。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NodeFeatureTestApplication.class, args);
    }
}

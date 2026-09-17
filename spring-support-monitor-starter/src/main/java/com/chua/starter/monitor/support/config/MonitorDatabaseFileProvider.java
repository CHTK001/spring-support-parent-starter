package com.chua.starter.monitor.support.config;

import com.chua.starter.datasource.support.DatabaseFileProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 监控模块数据库脚本提供者
 * 自动注册 flyway 迁移脚本
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class MonitorDatabaseFileProvider implements DatabaseFileProvider {

    /**
    * 是否启用当前提供者。
    *
    * @return 恒为 true，监控模块始终参与脚本注册
    */
    @Override
    public boolean isSupported() {
        return true;
    }

    /**
    * 加载 classpath 下 {@code db/init/V1.1__*.sql} 迁移脚本。
    *
    * @return 匹配到的脚本资源列表，加载异常时返回空列表
    */
    @Override
    public List<Resource> getResources() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            return Arrays.asList(resolver.getResources("classpath*:db/init/V1.1__*.sql"));
        } catch (Exception e) {
            // 资源缺失或解析异常时降级为空，避免阻断应用启动
            return List.of();
        }
    }

    /**
    * 执行顺序，数值越小优先级越高。
    *
    * @return 固定为 200
    */
    @Override
    public int getOrder() {
        return 200;
    }
}

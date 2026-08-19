package com.chua.starter.spider.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 公共字段自动填充配置。
 *
 * <p>独立于 {@link SpiderAutoConfiguration} 存在，避免节点链路（节点-服务-Mapper-SqlSessionFactory）
 * 与 SqlSessionFactory 依赖 MetaObjectHandler Bean 之间形成循环依赖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.chua.starter.mybatis.MybatisPlusConfiguration")
@ConditionalOnProperty(prefix = "spider", name = "enable", havingValue = "true", matchIfMissing = true)
public class SpiderMetaObjectHandlerConfiguration {

    /**
     * 注册 MyBatis-Plus 公共字段自动填充处理器。
     *
     * <p>按 {@code SysBase} 字段约定在新增/更新时自动填充创建时间与更新时间，
     * 由 mybatis-starter 的 SqlSessionFactory 关联到 GlobalConfig。</p>
     *
     * @return 公共字段自动填充处理器
     */
    @Bean
    public MetaObjectHandler spiderMetaObjectHandler() {
        return new SpiderMetaObjectHandler();
    }
}
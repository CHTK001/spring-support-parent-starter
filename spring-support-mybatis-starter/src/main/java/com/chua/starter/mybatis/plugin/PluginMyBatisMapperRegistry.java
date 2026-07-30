package com.chua.starter.mybatis.plugin;

import org.apache.ibatis.session.SqlSessionFactory;

import java.net.URL;

/**
 * 插件 MyBatis Mapper 注册接口
 * <p>
 * 用于将插件中的 Mapper 接口和 XML 注册到主应用的 MyBatis 配置中
 * </p>
 *
 * @author CH
 * @since 2025/01/15
 */
public interface PluginMyBatisMapperRegistry {

    /**
     * 注册插件 Mapper 接口
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param mapperClasses     Mapper 接口类
     */
    void registerMappers(SqlSessionFactory sqlSessionFactory, Class<?>... mapperClasses);

    /**
     * 从插件 ClassLoader 中扫描并注册指定路径下的 Mapper XML
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param classLoader       插件 ClassLoader
     * @param mapperXmlPath     Mapper XML 路径(例如: {@code mapper})
     */
    void registerMapperXmls(SqlSessionFactory sqlSessionFactory,
                            ClassLoader classLoader,
                            String mapperXmlPath);

    /**
     * 注册单个 Mapper XML
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param mapperXmlUrl      Mapper XML 资源地址
     */
    void registerMapperXml(SqlSessionFactory sqlSessionFactory, URL mapperXmlUrl);
}


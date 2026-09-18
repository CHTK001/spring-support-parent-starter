package com.chua.starter.mybatis.plugin;

import org.apache.ibatis.session.SqlSessionFactory;

import java.net.URL;

/**
* 插件 MyBatis 映射器 注册接口
* <p>
* 用于将插件中的 映射器 接口和 XML 注册到主应用的 MyBatis 配置中
* </p>
*
* @since 2025/01/15
* @author CH
 */
public interface PluginMyBatisMapperRegistry {

    /**
    * 注册插件 映射器 接口
    *
    * @param sqlSessionFactory SQL会话工厂 实例
    * @param mapperClasses     映射器 接口类
    */
    void registerMappers(SqlSessionFactory sqlSessionFactory, Class<?>... mapperClasses);

    /**
    * 从插件 类加载 中扫描并注册指定路径下的 映射器 XML
    *
    * @param sqlSessionFactory SQL会话工厂 实例
    * @param classLoader       插件 类加载
    * @param mapperXmlPath     映射器 XML 路径(例如: {@code mapper})
    */
    void registerMapperXmls(SqlSessionFactory sqlSessionFactory,
                            ClassLoader classLoader,
                            String mapperXmlPath);

    /**
    * 注册单个 映射器 XML
    *
    * @param sqlSessionFactory SQL会话工厂 实例
    * @param mapperXmlUrl      映射器 XML 资源地址
    */
    void registerMapperXml(SqlSessionFactory sqlSessionFactory, URL mapperXmlUrl);
}


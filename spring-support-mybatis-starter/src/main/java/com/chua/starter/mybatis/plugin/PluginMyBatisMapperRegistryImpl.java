package com.chua.starter.mybatis.plugin;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.mybatis.plugin.PluginMyBatisMapperRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * 插件 MyBatis Mapper 注册工具默认实现。
 *
 * <p>该类通过 SPI 暴露给插件系统使用，推荐通过
 * {@link com.chua.common.support.plugin.mybatis.PluginMyBatisMapperRegistry#provider()}
 * 获取实例后调用。</p>
 *
 * <p>实现基于主应用的 {@link SqlSessionFactory} 完成：</p>
 * <ul>
 *     <li>插件 Mapper 接口注册</li>
 *     <li>插件 Mapper XML 注册</li>
 * </ul>
 *
 * <p>推荐使用共享 {@link SqlSessionFactory} 方案，保持事务与连接池统一。</p>
 *
 * @author CH
 * @since 2026/01/15
 */
@Slf4j
@Spi("default")
public class PluginMyBatisMapperRegistryImpl implements PluginMyBatisMapperRegistry {

    /**
     * 注册插件 Mapper 接口
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param mapperClasses     Mapper 接口类
     */
    @Override
    public void registerMappers(SqlSessionFactory sqlSessionFactory, Class<?>... mapperClasses) {
        if (sqlSessionFactory == null || mapperClasses == null || mapperClasses.length == 0) {
            return;
        }

        Configuration configuration = sqlSessionFactory.getConfiguration();
        for (Class<?> mapperClass : mapperClasses) {
            if (mapperClass == null) {
                continue;
            }
            if (!mapperClass.isInterface()) {
                log.warn("[PluginMyBatis][注册]忽略非接口类型: {}", mapperClass.getName());
                continue;
            }
            try {
                if (!configuration.hasMapper(mapperClass)) {
                    configuration.addMapper(mapperClass);
                    log.info("[PluginMyBatis][注册]Mapper 接口注册成功: {}", mapperClass.getName());
                } else {
                    log.debug("[PluginMyBatis][注册]Mapper 接口已存在, 跳过注册: {}", mapperClass.getName());
                }
            } catch (Exception e) {
                log.error("[PluginMyBatis][注册]Mapper 接口注册失败: {}", mapperClass.getName(), e);
            }
        }
    }

    /**
     * 从插件 ClassLoader 中扫描并注册指定路径下的 Mapper XML
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param classLoader       插件 ClassLoader
     * @param mapperXmlPath     Mapper XML 路径(例如: {@code mapper})
     */
    @Override
    public void registerMapperXmls(SqlSessionFactory sqlSessionFactory,
                                   ClassLoader classLoader,
                                   String mapperXmlPath) {
        if (sqlSessionFactory == null || classLoader == null) {
            return;
        }

        if (mapperXmlPath == null || mapperXmlPath.isEmpty()) {
            log.warn("[PluginMyBatis][注册]Mapper XML 路径为空");
            return;
        }

        try {
            Enumeration<URL> resources = classLoader.getResources(mapperXmlPath);
            if (!resources.hasMoreElements()) {
                log.debug("[PluginMyBatis][注册]未在插件中找到 Mapper XML 目录: {}", mapperXmlPath);
                return;
            }

            Configuration configuration = sqlSessionFactory.getConfiguration();
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                registerMapperXmlInternal(configuration, resourceUrl);
            }
        } catch (Exception e) {
            log.error("[PluginMyBatis][注册]Mapper XML 扫描失败, 路径: {}", mapperXmlPath, e);
        }
    }

    /**
     * 注册单个 Mapper XML
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param mapperXmlUrl      Mapper XML 资源地址
     */
    @Override
    public void registerMapperXml(SqlSessionFactory sqlSessionFactory, URL mapperXmlUrl) {
        if (sqlSessionFactory == null || mapperXmlUrl == null) {
            return;
        }

        try {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            registerMapperXmlInternal(configuration, mapperXmlUrl);
        } catch (Exception e) {
            log.error("[PluginMyBatis][注册]Mapper XML 注册失败: {}", mapperXmlUrl, e);
        }
    }

    /**
     * 内部通用 XML 注册逻辑
     *
     * @param configuration MyBatis 配置
     * @param mapperXmlUrl  Mapper XML 资源地址
     */
    private void registerMapperXmlInternal(Configuration configuration, URL mapperXmlUrl) {
        if (configuration == null || mapperXmlUrl == null) {
            return;
        }

        try (InputStream inputStream = mapperXmlUrl.openStream()) {
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    mapperXmlUrl.toString(),
                    configuration.getSqlFragments()
            );
            xmlMapperBuilder.parse();
            log.info("[PluginMyBatis][注册]Mapper XML 注册成功: {}", mapperXmlUrl);
        } catch (Exception e) {
            log.error("[PluginMyBatis][注册]Mapper XML 注册失败: {}", mapperXmlUrl, e);
        }
    }
}


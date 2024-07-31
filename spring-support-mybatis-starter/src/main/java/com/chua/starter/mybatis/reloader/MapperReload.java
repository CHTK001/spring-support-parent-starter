package com.chua.starter.mybatis.reloader;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 加载器
 *
 * @author CH
 */
@Slf4j
public class MapperReload implements Reload, DisposableBean {

    final PathMatchingResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
    private final Map<String, Resource> fileResources = new ConcurrentHashMap<>();
    private final List<SqlSessionFactory> sqlSessionFactories;
    private final MybatisPlusProperties mybatisProperties;

    private final Map<String, FileAlterationMonitor> monitorMap = new ConcurrentHashMap<>();

    public MapperReload(List<SqlSessionFactory> sqlSessionFactories, MybatisPlusProperties mybatisProperties) {
        this.sqlSessionFactories = sqlSessionFactories;
        this.mybatisProperties = mybatisProperties;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            Resource[] resources = null;
            try {
                resources = patternResolver.getResources("classpath*:**/*Mapper.xml");
            } catch (IOException ignored) {
                return;
            }
            for (Resource resource : resources) {
                if (resource.isFile()) {
                    fileResources.put(resource.getFilename(), resource);
                }
            }

            MybatisPlusProperties.ReloadType reloadType = mybatisProperties.getReloadType();
            if (reloadType == MybatisPlusProperties.ReloadType.AUTO) {
                listener();
            }
        });
    }

    private void listener() {
        for (Resource resource : fileResources.values()) {
            File file = null;
            try {
                file = resource.getFile();
            } catch (IOException e) {
                continue;
            }

            register(file);
        }
    }

    private void register(File source) {
        File parentFile = source.getParentFile();
        monitorMap.computeIfAbsent(parentFile.getAbsolutePath(), new Function<String, FileAlterationMonitor>() {
            @Override
            public FileAlterationMonitor apply(String s) {
                long interval = mybatisProperties.getReloadTime();
                FileAlterationObserver fileAlterationObserver = new FileAlterationObserver(parentFile);
                fileAlterationObserver.addListener(new FileAlterationListenerAdaptor() {
                    @Override
                    public void onFileChange(File file) {
                        reload(file.getName());
                        super.onFileChange(file);
                    }

                    @Override
                    public void onFileDelete(File file) {
                        super.onFileDelete(file);
                    }
                });

                FileAlterationMonitor monitor = new FileAlterationMonitor(interval, fileAlterationObserver);
                try {
                    monitor.start();
                } catch (Exception ignored) {
                }
                return monitor;
            }
        });

    }

    @Override
    public String reload(String mapperXml) {
        Resource resource = fileResources.get(mapperXml.endsWith(".xml") ? mapperXml : mapperXml + ".xml");
        if (null == resource) {
            return mapperXml + "文件不存在";
        }

        try {
            reload(sqlSessionFactories, resource);
        } catch (RuntimeException e) {
            return "重载失败";
        }

        return "重载成功";
    }

    @SuppressWarnings("ALL")
    private void reload(List<SqlSessionFactory> sqlSessionFactories, Resource resource) throws RuntimeException {
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {

            try {
                Configuration targetConfiguration = sqlSessionFactory.getConfiguration();
                Class<?> tClass = targetConfiguration.getClass(), aClass = targetConfiguration.getClass();
                if ("MybatisConfiguration".equals(targetConfiguration.getClass().getSimpleName())) {
                    aClass = Configuration.class;
                }

                Set<String> loadedResources = (Set<String>) getFieldValue(targetConfiguration, aClass, "loadedResources");
                loadedResources.clear();

                Map<String, ResultMap> resultMaps = (Map<String, ResultMap>) getFieldValue(targetConfiguration, tClass, "resultMaps");
                Map<String, XNode> sqlFragmentsMaps = (Map<String, XNode>) getFieldValue(targetConfiguration, tClass, "sqlFragments");
                Map<String, MappedStatement> mappedStatementMaps = (Map<String, MappedStatement>) getFieldValue(targetConfiguration, tClass, "mappedStatements");

                XPathParser parser = new XPathParser(resource.getInputStream(), true, targetConfiguration.getVariables(), new XMLMapperEntityResolver());
                XNode mapperXnode = parser.evalNode("/mapper");
                List<XNode> resultMapNodes = mapperXnode.evalNodes("/mapper/resultMap");
                String namespace = mapperXnode.getStringAttribute("namespace");
                for (XNode xNode : resultMapNodes) {
                    String id = xNode.getStringAttribute("id", xNode.getValueBasedIdentifier());
                    resultMaps.remove(namespace + "." + id);
                }

                List<XNode> sqlNodes = mapperXnode.evalNodes("/mapper/sql");
                for (XNode sqlNode : sqlNodes) {
                    String id = sqlNode.getStringAttribute("id", sqlNode.getValueBasedIdentifier());
                    sqlFragmentsMaps.remove(namespace + "." + id);
                }

                List<XNode> msNodes = mapperXnode.evalNodes("select|insert|update|delete");
                for (XNode msNode : msNodes) {
                    String id = msNode.getStringAttribute("id", msNode.getValueBasedIdentifier());
                    mappedStatementMaps.remove(namespace + "." + id);
                }
                try {
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(resource.getInputStream(),
                            targetConfiguration, resource.toString(), targetConfiguration.getSqlFragments());
                    xmlMapperBuilder.parse();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                log.info("mapperLocation reload success: '{}'", resource);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 根据反射获取Configuration对象中属性
     *
     * @param targetConfiguration object
     * @param aClass              type
     * @param field               field
     * @return Exception    e
     */
    public static Object getFieldValue(Configuration targetConfiguration, Class<?> aClass, String field) throws Exception {
        Field resultMapsField = aClass.getDeclaredField(field);
        ClassUtils.setAccessible(resultMapsField);
        return resultMapsField.get(targetConfiguration);
    }

    @Override
    public void destroy() throws Exception {
        for (FileAlterationMonitor monitor : monitorMap.values()) {
            try {
                monitor.stop();
            } catch (Exception ignored) {
            }
        }
    }
}

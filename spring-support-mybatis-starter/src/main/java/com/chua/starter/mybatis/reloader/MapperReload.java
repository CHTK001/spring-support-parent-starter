package com.chua.starter.mybatis.reloader;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Map<String, WatchService> watchServiceMap = new ConcurrentHashMap<>();
    private final Map<String, Thread> watchThreadMap = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService watchExecutor;

    public MapperReload(List<SqlSessionFactory> sqlSessionFactories, MybatisPlusProperties mybatisProperties) {
        this.sqlSessionFactories = sqlSessionFactories;
        this.mybatisProperties = mybatisProperties;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        watchExecutor = ThreadUtils.newStaticThreadPool();
        watchExecutor.execute(() -> {
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
        String parentPath = parentFile.getAbsolutePath();
        
        watchServiceMap.computeIfAbsent(parentPath, path -> {
            try {
                Path watchPath = Paths.get(path);
                WatchService watchService = FileSystems.getDefault().newWatchService();
                
                // 注册文件修改和删除事件
                watchPath.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
                
                // 启动监听线程
                Thread watchThread = new Thread(() -> {
                    try {
                        while (running.get()) {
                            WatchKey key = watchService.poll(mybatisProperties.getReloadTime(), TimeUnit.MILLISECONDS);
                            if (key == null) {
                                continue;
                            }
                            
                            for (WatchEvent<?> event : key.pollEvents()) {
                                WatchEvent.Kind<?> kind = event.kind();
                                
                                if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                    @SuppressWarnings("unchecked")
                                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                                    Path fileName = ev.context();
                                    
                                    // 只处理 XML 文件
                                    if (fileName.toString().endsWith(".xml")) {
                                        String mapperXml = fileName.toString();
                                        log.debug("Detected file change: {}", mapperXml);
                                        reload(mapperXml);
                                    }
                                }
                            }
                            
                            boolean valid = key.reset();
                            if (!valid) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("Watch thread interrupted for path: {}", path);
                    } catch (Exception e) {
                        log.error("Error in watch thread for path: {}", path, e);
                    }
                }, "MapperReload-Watch-" + path);
                
                watchThread.setDaemon(true);
                watchThread.start();
                watchThreadMap.put(parentPath, watchThread);
                
                return watchService;
            } catch (IOException e) {
                log.error("Failed to create watch service for path: {}", path, e);
                return null;
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
        running.set(false);
        
        // 关闭所有 WatchService
        for (Map.Entry<String, WatchService> entry : watchServiceMap.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                log.debug("Error closing watch service for path: {}", entry.getKey(), e);
            }
        }
        
        // 等待所有监听线程结束
        for (Map.Entry<String, Thread> entry : watchThreadMap.entrySet()) {
            try {
                Thread thread = entry.getValue();
                if (thread != null && thread.isAlive()) {
                    thread.interrupt();
                    thread.join(1000); // 等待最多1秒
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Interrupted while waiting for watch thread: {}", entry.getKey());
            }
        }
        
        watchServiceMap.clear();
        watchThreadMap.clear();
        
        // 关闭线程池
        if (watchExecutor != null) {
            watchExecutor.shutdown();
        }
    }
}

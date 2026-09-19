package com.chua.starter.mybatis.reloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chua.common.support.reflection.ReflectUtils;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 映射器 XML文件热重载加载器
 * <p>
 * 支持监听文件系统中的映射器 XML文件变化，自动重新加载到MyBatis配置中。
 * 支持类路径资源和本地文件系统资源的加载和监听。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public class MapperReload implements Reload, DisposableBean {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MapperReload.class);

    /**
    * 资源模式匹配解析器
    */
    final PathMatchingResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
    
    /**
     * 文件资源映射表，Key为文件名，值为资源对象
     */
    private final Map<String, Resource> fileResources = new ConcurrentHashMap<>();
    
    /**
     * SQL会话工厂列表
     */
    private final List<SqlSessionFactory> sqlSessionFactories;
    
    /**
     * MyBatis Plus配置属性
     */
    private final MybatisPlusProperties mybatisProperties;

    /**
     * 文件监听服务映射表，Key为目录路径，值为watch服务
     */
    private final Map<String, WatchService> watchServiceMap = new ConcurrentHashMap<>();
    
    /**
     * 监听线程映射表，Key为目录路径，值为监听线程
     */
    private final Map<String, Thread> watchThreadMap = new ConcurrentHashMap<>();
    
    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    /**
     * 监听执行器
     */
    private ExecutorService watchExecutor;

    /**
     * 构造函数
     *
     * @param sqlSessionFactories SQL会话工厂列表
     * @param mybatisProperties MyBatis Plus配置属性
     */
    public MapperReload(List<SqlSessionFactory> sqlSessionFactories, MybatisPlusProperties mybatisProperties) {
        this.sqlSessionFactories = sqlSessionFactories;
        this.mybatisProperties = mybatisProperties;
    }


    /**
     * 初始化方法，在属性设置后调用
     * <p>
     * 加载所有映射器 XML资源，并根据配置决定是否启动文件监听
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        watchExecutor = ThreadUtils.newStaticThreadPool();
        watchExecutor.execute(() -> {
            try {
 // 加载默认类路径资源
                /**
                 * 加载类路径resources。
                 */
                loadClasspathResources();
                
                // 加载配置的目录资源
                /**
                 * 加载configured目录。
                 */
                loadConfiguredDirectories();
                
                MybatisPlusProperties.ReloadType reloadType = mybatisProperties.getReloadType();
                if (reloadType == MybatisPlusProperties.ReloadType.AUTO) {
                    /**
                     * 监听器。
                     */
                    listener();
                }
            } catch (Exception e) {
                log.error("初始化热重载失败", e);
            }
        });
    }

    /**
     * 加载类路径中的映射器 XML资源
     * <p>
     * 扫描类路径下所有以映射器.xml结尾的文件并加载到资源映射表中
     * </p>
     */
    private void loadClasspathResources() {
        try {
            Resource[] resources = patternResolver.getResources("classpath*:**/*Mapper.xml");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    fileResources.put(filename, resource);
                }
            }
        } catch (IOException e) {
            log.warn("加载classpath资源失败", e);
        }
    }

    /**
     * 加载配置的目录资源
     * <p>
     * 根据配置的目录列表，加载类路径或本地文件系统中的映射器 XML文件
     * </p>
     */
    private void loadConfiguredDirectories() {
        List<MybatisPlusProperties.ReloadDirectory> directories = mybatisProperties.getReloadDirectories();
        if (directories == null || directories.isEmpty()) {
            return;
        }

        for (MybatisPlusProperties.ReloadDirectory directory : directories) {
            try {
                String path = directory.getPath();
                String pattern = directory.getPattern();
                
                if (path == null || path.isEmpty()) {
                    continue;
                }

 // 判断是类路径路径还是本地路径
                if (path.startsWith("classpath") || path.startsWith("classpath*")) {
                    /**
                     * 加载类路径模式。
                     * @param path 路径
                     * @param pattern 模式
                     */
                    loadClasspathPattern(path, pattern);
                } else {
                    /**
                     * 加载本地目录。
                     * @param path 路径
                     * @param pattern 模式
                     * @param directory.isWatchEnabled( 目录.是否watch已启用(
                     */
                    loadLocalDirectory(path, pattern, directory.isWatchEnabled());
                }
            } catch (Exception e) {
                log.error("加载配置目录失败: {}", directory.getPath(), e);
            }
        }
    }

    /**
     * 加载类路径模式资源
     *
     * @param classpathPattern 类路径路径模式
     * @param filePattern 文件匹配模式
     */
    private void loadClasspathPattern(String classpathPattern, String filePattern) {
        try {
            String fullPattern = classpathPattern;
            if (!classpathPattern.endsWith("/") && !classpathPattern.endsWith("*")) {
                fullPattern = classpathPattern + "/" + filePattern;
            } else if (classpathPattern.endsWith("/")) {
                fullPattern = classpathPattern + filePattern;
            }
            
            Resource[] resources = patternResolver.getResources(fullPattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    fileResources.put(filename, resource);
                }
            }
        } catch (IOException e) {
            log.warn("加载classpath模式资源失败: {}", classpathPattern, e);
        }
    }

    /**
     * 加载本地目录资源
     *
     * @param localPath 本地目录路径
     * @param filePattern 文件匹配模式（glob格式）
     * @param watchEnabled 是否启用监听（暂未使用）
     */
    private void loadLocalDirectory(String localPath, String filePattern, boolean watchEnabled) {
        try {
            Path directoryPath = Paths.get(localPath);
            if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
                log.warn("目录不存在或不是目录: {}", localPath);
                return;
            }

            // 递归查找匹配的文件
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
            Files.walk(directoryPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .forEach(path -> {
                        try {
                            Resource resource = new FileSystemResource(path.toFile());
                            String filename = resource.getFilename();
                            if (filename != null) {
                                fileResources.put(filename, resource);
                            }
                        } catch (Exception e) {
                            log.warn("加载本地文件失败: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("加载本地目录失败: {}", localPath, e);
        }
    }

    /**
     * 启动文件监听
     * <p>
     * 只监听本地文件系统中的文件，jar包中的资源无法监听。
     * 为每个文件所在的目录创建watch服务，监听文件修改和删除事件。
     * </p>
     */
    private void listener() {
        for (Map.Entry<String, Resource> entry : fileResources.entrySet()) {
            Resource resource = entry.getValue();
            
            // 只监听文件系统中的资源
            if (resource instanceof FileSystemResource) {
                try {
                    File file = resource.getFile();
                    /**
                     * 注册。
                     * @param file 文件
                     */
                    register(file);
                } catch (IOException e) {
                    log.debug("无法获取文件对象，跳过监听: {}", entry.getKey(), e);
                }
            } else {
 // 检查是否是文件系统URI（开发环境中的类路径资源可能是文件系统）
                try {
                    URI uri = resource.getURI();
                    if ("file".equals(uri.getScheme())) {
                        File file = new File(uri);
                        if (file.exists() && file.isFile()) {
                            /**
                             * 注册。
                             * @param file 文件
                             */
                            register(file);
                        }
                    }
                } catch (Exception e) {
                    log.debug("资源无法监听（可能在jar包中）: {}", entry.getKey());
                }
            }
        }
    }

    /**
     * 注册文件监听
     *
     * @param source 源文件
     */
    private void register(File source) {
        File parentFile = source.getParentFile();
        String parentPath = parentFile.getAbsolutePath();
        
        watchServiceMap.computeIfAbsent(parentPath, path -> {
            HotReloadWatcher watcher = createHotReloadWatcher(path);
            if (watcher != null) {
                watchThreadMap.put(parentPath, watcher.getWatchThread());
                return watcher.getWatchService();
            }
            return null;
        });
    }

    /**
     * 创建热重载监听器
     *
     * @param path 监听路径
     * @return 热重载监听器，创建失败返回null
     */
    private HotReloadWatcher createHotReloadWatcher(String path) {
        try {
            Path watchPath = Paths.get(path);
            WatchService watchService = FileSystems.getDefault().newWatchService();
            
            // 注册文件修改和删除事件
            watchPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            
            // 创建监听线程
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
                                    /**
                                     * reload。
                                     * @param mapperXml 映射器xml
                                     */
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
            
            /**
             * hotreloadwatcher。
             * @param watchService watch服务
             * @param watchThread watchthread
             * @return HotReloadWatcher的结果
             */
            return new HotReloadWatcher(watchService, watchThread);
        } catch (IOException e) {
            log.error("Failed to create watch service for path: {}", path, e);
            return null;
        }
    }

    /**
     * 热重载监听器
     * 封装 watch服务 和监听线程
     * @author CH
     * @since 4.0.0
     */
    private static class HotReloadWatcher {
        /**
         * 文件监听服务
         */
        private final WatchService watchService;
        
        /**
         * 监听线程
         */
        private final Thread watchThread;

        /**
         * 构造函数
         *
         * @param watchService 文件监听服务
         * @param watchThread 监听线程
         */
        public HotReloadWatcher(WatchService watchService, Thread watchThread) {
            this.watchService = watchService;
            this.watchThread = watchThread;
        }

        /**
         * 获取文件监听服务
         *
         * @return 文件监听服务
         */
        public WatchService getWatchService() {
            return watchService;
        }

        /**
         * 获取监听线程
         *
         * @return 监听线程
         */
        public Thread getWatchThread() {
            return watchThread;
        }
    }

    /**
     * 重新加载指定的映射器 XML文件
     *
     * @param mapperXml 映射器 XML文件名或路径
     * @return 重载结果信息
     */
    @Override
    public String reload(String mapperXml) {
        // 支持文件名或完整路径
        String filename = mapperXml.endsWith(".xml") ? mapperXml : mapperXml + ".xml";
        Resource resource = fileResources.get(filename);
        
        // 如果按文件名找不到，尝试按路径查找
        if (resource == null) {
            for (Resource res : fileResources.values()) {
                try {
                    String resourcePath = res.getURI().toString();
                    if (resourcePath.contains(filename) || resourcePath.endsWith(filename)) {
                        resource = res;
                        break;
                    }
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
        
        if (resource == null) {
            return String.format("文件不存在: %s", mapperXml);
        }

        try {
            /**
             * reload。
             * @param sqlSessionFactories SQL会话工厂
             * @param resource resource
             */
            reload(sqlSessionFactories, resource);
            return String.format("重载成功: %s", filename);
        } catch (RuntimeException e) {
            log.error("重载失败: {}", mapperXml, e);
            return String.format("重载失败: %s, 错误: %s", mapperXml, e.getMessage());
        }
    }

    /**
     * 列出所有已加载的映射器 XML文件信息
     *
     * @return 文件信息列表
     */
    @Override
    public List<FileInfo> listFiles() {
        List<FileInfo> fileInfoList = new ArrayList<>();
        
        for (Map.Entry<String, Resource> entry : fileResources.entrySet()) {
            String filename = entry.getKey();
            Resource resource = entry.getValue();
            
            FileInfo fileInfo = new FileInfo();
            
            try {
                URI uri = resource.getURI();
                fileInfo.name = filename;
                fileInfo.path = uri.toString();
                
                // 判断资源类型和是否可监听
                String scheme = uri.getScheme();
                if ("file".equals(scheme)) {
                    fileInfo.type = "FILE";
                    fileInfo.watchable = true;
                } else if ("jar".equals(scheme) || "war".equals(scheme)) {
                    fileInfo.type = "JAR";
                    fileInfo.watchable = false;
                } else {
                    fileInfo.type = "CLASSPATH";
 // 类路径资源如果在文件系统中，可以监听
                    try {
                        File file = resource.getFile();
                        fileInfo.watchable = file.exists();
                    } catch (IOException e) {
                        fileInfo.watchable = false;
                    }
                }
            } catch (IOException e) {
                fileInfo.name = filename;
                fileInfo.path = "未知路径";
                fileInfo.type = "UNKNOWN";
                fileInfo.watchable = false;
            }
            
            fileInfoList.add(fileInfo);
        }
        
        return fileInfoList;
    }

    /**
     * 重新加载映射器 XML资源到所有SQL会话工厂
     * <p>
     * 先清理旧的映射器配置（结果映射、sqlfragments、mapped对账单），
     * 然后重新解析XML文件并加载到配置中。
     * </p>
     *
     * @param sqlSessionFactories SQL会话工厂列表
     * @param resource 映射器 XML资源
     * @throws RuntimeException 重载异常
     */
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
                /**
                 * runtime异常。
                 * @param e e
                 * @return runtime异常的结果
                 */
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 根据反射获取配置对象中属性值
     *
     * @param targetConfiguration 配置对象
     * @param aClass 目标类型
     * @param field 字段名
     * @return 字段值
     */
    public static Object getFieldValue(Configuration targetConfiguration, Class<?> aClass, String field) {
        // [P3C 1.10] 改走 ReflectUtils.getField，底层已处理 setAccessible
        Object value = ReflectUtils.getField(targetConfiguration, field);
        if (value == null) {
            log.error("反射获取字段失败: {}.{}", aClass.getSimpleName(), field);
        }
        return value;
    }

    /**
     * 销毁方法，关闭所有监听服务和线程
     * <p>
     * 停止运行标志，关闭所有watch服务，等待监听线程结束，清理资源
     * </p>
     *
     * @throws Exception 销毁异常
     */
    @Override
    public void destroy() throws Exception {
        running.set(false);
        
 // 关闭所有 watch服务
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

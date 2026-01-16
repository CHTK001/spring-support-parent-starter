package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.AbstractProtocol;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OAuth客户端白名单文件监听器
 * <p>
 * 监听外部 oauth.whitelist 文件的变更，动态更新白名单配置。
 * 支持以下功能：
 * <ul>
 * <li>启动时合并外部白名单文件与配置文件白名单</li>
 * <li>文件变更时自动重新加载白名单</li>
 * <li>白名单变更后持久化到外部文件</li>
 * <li>支持通过API动态添加/删除白名单</li>
 * </ul>
 * </p>
 * <p>
 * 白名单文件格式：每行一个URL Pattern，支持 # 注释
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhitelistFileWatcher {

    private final AuthClientProperties authClientProperties;

    /**
     * 合并后的白名单（线程安全）
     */
    private final CopyOnWriteArrayList<String> mergedWhitelist = new CopyOnWriteArrayList<>();

    /**
     * 文件监听线程
     */
    private ExecutorService watchExecutor;
    private WatchService watchService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 初始化：加载外部白名单文件并启动监听
     */
    @PostConstruct
    public void initialize() {
        log.info("[白名单监听]初始化开始 - 文件路径: {}", authClientProperties.getWhitelistFile());
        
        // 1. 合并配置文件白名单和外部文件白名单
        loadAndMergeWhitelist();
        
        // 2. 启动文件监听
        startFileWatcher();
        
        log.info("[白名单监听]初始化完成 - 合并后白名单数量: {}", mergedWhitelist.size());
    }

    /**
     * 加载并合并白名单
     */
    private void loadAndMergeWhitelist() {
        mergedWhitelist.clear();
        
        // 添加配置文件中的白名单
        List<String> configWhitelist = authClientProperties.getWhitelist();
        if (configWhitelist != null && !configWhitelist.isEmpty()) {
            mergedWhitelist.addAll(configWhitelist);
            log.info("[白名单监听]加载配置文件白名单: {} 条", configWhitelist.size());
        }
        
        // 加载外部文件白名单
        List<String> fileWhitelist = loadWhitelistFromFile();
        for (String pattern : fileWhitelist) {
            if (!mergedWhitelist.contains(pattern)) {
                mergedWhitelist.add(pattern);
            }
        }
        
        // 更新到 AuthClientProperties
        authClientProperties.setWhitelist(new ArrayList<>(mergedWhitelist));
        log.info("[白名单监听]白名单合并完成 - 总数: {}", mergedWhitelist.size());
    }

    /**
     * 从外部文件加载白名单
     */
    private List<String> loadWhitelistFromFile() {
        List<String> whitelist = new ArrayList<>();
        Path path = Paths.get(authClientProperties.getWhitelistFile());
        
        if (!Files.exists(path)) {
            log.info("[白名单监听]外部白名单文件不存在，将创建: {}", authClientProperties.getWhitelistFile());
            createWhitelistFile(path);
            return whitelist;
        }
        
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                // 忽略空行和注释
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    whitelist.add(trimmed);
                }
            }
            log.info("[白名单监听]从文件加载白名单: {} 条", whitelist.size());
        } catch (IOException e) {
            log.error("[白名单监听]读取白名单文件失败: {}", e.getMessage());
        }
        
        return whitelist;
    }

    /**
     * 创建白名单文件
     */
    private void createWhitelistFile(Path path) {
        try {
            // 创建父目录
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            // 创建文件并写入示例内容
            List<String> lines = Arrays.asList(
                    "# OAuth 白名单配置文件",
                    "# 每行一个URL Pattern，支持Ant风格匹配",
                    "# 示例:",
                    "# /public/**",
                    "# /api/health",
                    "# /static/**",
                    ""
            );
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("[白名单监听]创建白名单文件成功: {}", path);
        } catch (IOException e) {
            log.error("[白名单监听]创建白名单文件失败: {}", e.getMessage());
        }
    }

    /**
     * 启动文件监听
     */
    private void startFileWatcher() {
        try {
            Path path = Paths.get(authClientProperties.getWhitelistFile());
            Path dir = path.getParent();
            
            if (dir == null) {
                dir = Paths.get(".");
            }
            
            // 确保目录存在
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            
            running.set(true);
            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "oauth-whitelist-watcher");
                t.setDaemon(true);
                return t;
            });
            
            Path fileName = path.getFileName();
            watchExecutor.submit(() -> watchFileChanges(fileName));
            
            log.info("[白名单监听]文件监听已启动 - 监听目录: {}", dir);
        } catch (IOException e) {
            log.error("[白名单监听]启动文件监听失败: {}", e.getMessage());
        }
    }

    /**
     * 监听文件变更
     */
    private void watchFileChanges(Path fileName) {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path changed = ev.context();
                    
                    // 检查是否是白名单文件
                    if (changed.equals(fileName)) {
                        log.info("[白名单监听]检测到文件变更: {}", changed);
                        // 延迟一下，确保文件写入完成
                        Thread.sleep(100);
                        reloadWhitelist();
                    }
                }
                
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[白名单监听]监听异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 重新加载白名单
     */
    public void reloadWhitelist() {
        log.info("[白名单监听]开始重新加载白名单...");
        loadAndMergeWhitelist();
        // 清除认证缓存
        AbstractProtocol.invalidateAllCache();
        log.info("[白名单监听]白名单重新加载完成，已清除认证缓存");
    }

    /**
     * 添加白名单（动态添加并持久化）
     *
     * @param pattern URL Pattern
     * @return 是否添加成功
     */
    public boolean addWhitelist(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return false;
        }
        
        String trimmed = pattern.trim();
        if (mergedWhitelist.contains(trimmed)) {
            log.info("[白名单监听]白名单已存在: {}", trimmed);
            return false;
        }
        
        mergedWhitelist.add(trimmed);
        authClientProperties.setWhitelist(new ArrayList<>(mergedWhitelist));
        persistWhitelist();
        
        log.info("[白名单监听]添加白名单成功: {}", trimmed);
        return true;
    }

    /**
     * 删除白名单（动态删除并持久化）
     *
     * @param pattern URL Pattern
     * @return 是否删除成功
     */
    public boolean removeWhitelist(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return false;
        }
        
        String trimmed = pattern.trim();
        boolean removed = mergedWhitelist.remove(trimmed);
        
        if (removed) {
            authClientProperties.setWhitelist(new ArrayList<>(mergedWhitelist));
            persistWhitelist();
            log.info("[白名单监听]删除白名单成功: {}", trimmed);
        }
        
        return removed;
    }

    /**
     * 获取当前白名单列表
     */
    public List<String> getWhitelist() {
        return new ArrayList<>(mergedWhitelist);
    }

    /**
     * 持久化白名单到文件
     */
    private void persistWhitelist() {
        Path path = Paths.get(authClientProperties.getWhitelistFile());
        
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# OAuth 白名单配置文件");
            lines.add("# 自动生成于: " + java.time.LocalDateTime.now());
            lines.add("# 每行一个URL Pattern，支持Ant风格匹配");
            lines.add("");
            
            // 只持久化非配置文件中的白名单
            List<String> configWhitelist = authClientProperties.getWhitelist();
            for (String pattern : mergedWhitelist) {
                lines.add(pattern);
            }
            
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("[白名单监听]白名单已持久化到文件: {}", path);
        } catch (IOException e) {
            log.error("[白名单监听]持久化白名单失败: {}", e.getMessage());
        }
    }

    /**
     * 关闭资源
     */
    @PreDestroy
    public void shutdown() {
        running.set(false);
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("[白名单监听]关闭WatchService失败: {}", e.getMessage());
            }
        }
        
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
        }
        
        log.info("[白名单监听]已关闭");
    }
}

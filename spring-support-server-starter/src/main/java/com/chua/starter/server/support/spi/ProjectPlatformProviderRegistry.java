package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 发布模块平台命令提供者注册中心。
 *
 * <p>实现按 {@code @Spi} 注册到 {@link ProjectPlatformProvider} 扩展点
 * （{@code META-INF/extensions} 文件 + 接口同包扫描两条发现路径），本类按其
 * {@link ProjectPlatformProvider#osType()} 建索引；发布业务（备份/回滚/执行包装/
 * 变量块/外部配置等）据此取命令，业务层不再持有平台分支。</p>
 *
 * <p>索引在首次解析时构建：SPI 实例的依赖由 Spring 装配链注入，容器就绪前构建会得到半初始化实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ProjectPlatformProviderRegistry {

    /**
     * 平台命令扩展点
     */
    private final ServiceProvider<ProjectPlatformProvider> extensions = ServiceProvider.of(ProjectPlatformProvider.class);

    /**
     * 系统类型 -> 平台命令实现索引，首次使用时构建
     */
    private volatile Map<String, ProjectPlatformProvider> index;

    /**
     * 按系统类型解析平台命令提供者。
     *
     * @param osType 系统类型（LINUX/MACOS/WINDOWS/...，大小写不敏感）
     * @return Optional，未登记该平台时为空
     */
    public Optional<ProjectPlatformProvider> resolve(String osType) {
        if (osType == null || osType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(osType.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * 是否支持指定系统类型。
     *
     * @param osType 系统类型
     * @return 是否支持
     */
    public boolean supports(String osType) {
        return resolve(osType).isPresent();
    }

    /**
     * 列出已登记的平台标识。
     *
     * @return 平台标识列表
     */
    public List<String> supportedOsTypes() {
        return List.copyOf(index().keySet());
    }

    /**
     * 解析平台命令提供者，并按 POSIX 家族兜底。
     *
     * <p>已登记平台（LINUX/MACOS/WINDOWS）直接命中；未登记的 POSIX 家族平台
     * （FREEBSD/AIX/SOLARIS 等）回落到 POSIX 实现，避免因未登记而中断发布 ——
     * 这与改动前的行为一致，区别是从「业务层 windows 二选一」变为「注册中心显式兜底」。
     * 仅 WINDOWS 不会被兜底（缺失即视为配置错误）。</p>
     *
     * @param osType 系统类型
     * @return 平台命令提供者
     */
    public ProjectPlatformProvider resolveOrPosix(String osType) {
        var hit = resolve(osType);
        if (hit.isPresent()) {
            return hit.get();
        }
        if (osType != null && "WINDOWS".equalsIgnoreCase(osType.trim())) {
            throw new IllegalStateException("未注册 Windows 平台命令实现（ProjectPlatformProvider）");
        }
        var posix = index().get("LINUX");
        if (posix == null) {
            throw new IllegalStateException("未注册任何 POSIX 平台命令实现（ProjectPlatformProvider）");
        }
        log.warn("[project-platform] 平台「{}」未登记，按 POSIX 实现兜底：{}", osType, posix.getClass().getSimpleName());
        return posix;
    }

    /**
     * 取索引，必要时构建。
     *
     * @return 系统类型 -> 实现
     */
    private Map<String, ProjectPlatformProvider> index() {
        Map<String, ProjectPlatformProvider> current = index;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (index == null) {
                index = build();
            }
            return index;
        }
    }

    /**
     * 遍历扩展点定义建索引，同一实现类的多个别名只登记一次。
     *
     * @return 不可变索引
     */
    private Map<String, ProjectPlatformProvider> build() {
        Map<String, ProjectPlatformProvider> out = new HashMap<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            ProjectPlatformProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            String osType = provider.osType();
            if (osType == null || osType.isBlank()) {
                continue;
            }
            out.put(osType.trim().toUpperCase(Locale.ROOT), provider);
            log.info("[project-platform] 注册平台命令 Provider: osType={}, impl={}",
                    osType, provider.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[project-platform] ProjectPlatformProvider 扩展点无任何实现，发布命令不可用");
        }
        return Collections.unmodifiableMap(out);
    }
}

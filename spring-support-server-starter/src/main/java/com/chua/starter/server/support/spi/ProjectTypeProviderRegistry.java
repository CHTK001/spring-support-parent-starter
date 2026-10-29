package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.provider.project.type.GenericProjectTypeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 发布模块「项目类型」提供者注册中心。
 *
 * <p>实现按 {@code @Spi} 注册到 {@link ProjectTypeProvider} 扩展点
 * （{@code META-INF/extensions} 文件 + 接口同包扫描两条发现路径），本类按其
 * {@link ProjectTypeProvider#type()} 与 {@link ProjectTypeProvider#aliases()} 建索引；
 * 发布业务（脚本模板、升级投放、重启判定、系统服务前台命令等）据此取类型行为，
 * 业务层不再持有项目类型分支。</p>
 *
 * <p>索引在首次解析时构建：SPI 实例的依赖由装配链注入，容器就绪前构建会得到半初始化实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ProjectTypeProviderRegistry {

    /**
     * 项目类型扩展点
     */
    private final ServiceProvider<ProjectTypeProvider> extensions = ServiceProvider.of(ProjectTypeProvider.class);

    /**
     * 项目类型 -> 类型实现索引，首次使用时构建
     */
    private volatile Map<String, ProjectTypeProvider> index;

    /**
     * 按项目类型解析类型实现。
     *
     * @param type 项目类型（大小写不敏感）
     * @return Optional，未登记该类型时为空
     */
    public Optional<ProjectTypeProvider> resolve(String type) {
        String key = key(type);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(key));
    }

    /**
     * 解析项目类型实现，未登记时回落到通用实现。
     *
     * <p>回落到「通用」而不是抛异常：项目类型是历史自由文本字段，已有数据里可能存在
     * 空值或旧标识，升级链路不能因为一个未知类型直接不可用。回落时记一条告警，
     * 便于发现拼写错误。</p>
     *
     * @param type 项目类型
     * @return 类型实现，永不为 {@code null}
     */
    public ProjectTypeProvider resolveOrGeneric(String type) {
        var hit = resolve(type);
        if (hit.isPresent()) {
            return hit.get();
        }
        log.warn("[project-type] 项目类型「{}」未登记，按通用类型兜底", type);
        return GenericProjectTypeProvider.INSTANCE;
    }

    /**
     * 是否登记了指定项目类型。
     *
     * @param type 项目类型
     * @return 已登记时为 {@code true}
     */
    public boolean supports(String type) {
        return resolve(type).isPresent();
    }

    /**
     * 列出已登记的项目类型标识（含别名）。
     *
     * @return 类型标识列表
     */
    public List<String> supportedTypes() {
        return List.copyOf(index().keySet());
    }

    /**
     * 列出已登记的类型实现（同一实现类的多个别名只返回一次，按类型标识排序）。
     *
     * @return 类型实现列表
     */
    public List<ProjectTypeProvider> providers() {
        List<ProjectTypeProvider> out = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ProjectTypeProvider provider : index().values()) {
            if (seen.add(provider.getClass())) {
                out.add(provider);
            }
        }
        out.sort((a, b) -> a.type().compareToIgnoreCase(b.type()));
        return List.copyOf(out);
    }

    /**
     * 取索引，必要时构建。
     *
     * @return 项目类型 -> 实现
     */
    private Map<String, ProjectTypeProvider> index() {
        Map<String, ProjectTypeProvider> current = index;
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
    private Map<String, ProjectTypeProvider> build() {
        Map<String, ProjectTypeProvider> out = new HashMap<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            ProjectTypeProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            String type = key(provider.type());
            if (type.isEmpty()) {
                log.warn("[project-type] 忽略未声明类型的实现: {}", provider.getClass().getName());
                continue;
            }
            out.put(type, provider);
            for (String alias : provider.aliases()) {
                String aliasKey = key(alias);
                if (!aliasKey.isEmpty()) {
                    out.put(aliasKey, provider);
                }
            }
            log.info("[project-type] 注册项目类型 Provider: type={}, impl={}",
                    type, provider.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[project-type] ProjectTypeProvider 扩展点无任何实现，发布脚本与升级投放不可用");
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * 类型标识归一（去空白 + 小写）。
     *
     * @param type 原始类型
     * @return 归一后的标识；{@code null} 归一为空串
     */
    private static String key(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}

package com.chua.starter.datasearch.provider.limit;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * 限制机制注册表：把 {@link ProviderLimitMechanism} 的各种接入方式收敛成一条有序链。
 *
 * <p>装载优先级（同一 {@code kind} 只保留一个）：</p>
 * <ol>
 *   <li>Spring 容器里的 Bean（宿主装配类启动时调用 {@link #install}）；</li>
 *   <li>{@code META-INF/services} 登记的实现（{@link ServiceLoader}）；</li>
 *   <li>内置兜底（手动 / 官方文档 / 响应头），仅在容器和 SPI 文件都没有时使用。</li>
 * </ol>
 *
 * <p>结果按 {@link ProviderLimitMechanism#order()} 升序排列，顺序即「谁先命中谁生效」。
 * 注册表在首次使用时构建一次；{@link #install} 可覆盖（容器启动、测试重置）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public final class ProviderLimitMechanisms {

    private static final Object LOCK = new Object();

    /**
     * 已安装的机制链；null 表示尚未解析
     */
    private static volatile List<ProviderLimitMechanism> installed;

    private ProviderLimitMechanisms() {
    }

    /**
     * 当前生效的机制链（首次调用时解析并缓存）
     *
     * @return 有序、不可变的机制列表
     */
    public static List<ProviderLimitMechanism> mechanisms() {
        List<ProviderLimitMechanism> current = installed;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (installed == null) {
                installed = resolve(List.of());
            }
            return installed;
        }
    }

    /**
     * 安装 Spring 容器里的机制实现；与 ServiceLoader 结果合并后覆盖当前链。
     *
     * @param springMechanisms 容器注入的实现（可为空）
     */
    public static void install(List<ProviderLimitMechanism> springMechanisms) {
        synchronized (LOCK) {
            installed = resolve(springMechanisms == null ? List.of() : springMechanisms);
            log.info("[ProviderLimit] 机制链已装载：{}", kinds());
        }
    }

    /**
     * 清空缓存，下次 {@link #mechanisms()} 重新解析（测试用）。
     */
    public static void reset() {
        synchronized (LOCK) {
            installed = null;
        }
    }

    /**
     * 机制标识清单，按执行顺序。
     *
     * @return 机制标识
     */
    public static List<String> kinds() {
        List<ProviderLimitMechanism> current = mechanisms();
        List<String> kinds = new ArrayList<>(current.size());
        for (ProviderLimitMechanism mechanism : current) {
            kinds.add(mechanism.kind());
        }
        return kinds;
    }

    /**
     * 合并 Spring Bean、ServiceLoader 与内置兜底，按 kind 去重、按 order 排序。
     *
     * @param springMechanisms 容器注入的实现
     * @return 合并结果
     */
    private static List<ProviderLimitMechanism> resolve(List<ProviderLimitMechanism> springMechanisms) {
        Map<String, ProviderLimitMechanism> byKind = new LinkedHashMap<>(8);
        for (ProviderLimitMechanism mechanism : springMechanisms) {
            if (mechanism != null && mechanism.kind() != null) {
                byKind.put(mechanism.kind(), mechanism);
            }
        }
        for (ProviderLimitMechanism mechanism : loadServices()) {
            if (mechanism.kind() != null) {
                byKind.putIfAbsent(mechanism.kind(), mechanism);
            }
        }
        if (byKind.isEmpty()) {
            // 没有容器也没有 SPI 文件（例如裁剪过的运行镜像）时退回内置实现
            for (ProviderLimitMechanism mechanism : builtins()) {
                byKind.put(mechanism.kind(), mechanism);
            }
        }
        List<ProviderLimitMechanism> list = new ArrayList<>(byKind.values());
        list.sort(Comparator.comparingInt(ProviderLimitMechanism::order)
                .thenComparing(ProviderLimitMechanism::kind));
        return List.copyOf(list);
    }

    /**
     * 从 {@code META-INF/services} 加载扩展实现；单个实现加载失败不影响其它机制。
     *
     * @return 已加载的实现
     */
    private static List<ProviderLimitMechanism> loadServices() {
        List<ProviderLimitMechanism> list = new ArrayList<>(4);
        try {
            for (ProviderLimitMechanism mechanism : ServiceLoader.load(ProviderLimitMechanism.class)) {
                list.add(mechanism);
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            log.warn("[ProviderLimit] ServiceLoader 装载失败，仅使用已加载的机制：{}", e.toString());
        }
        return list;
    }

    /**
     * 内置兜底实现（与 ServiceLoader 文件中的三条一致）。
     *
     * @return 内置机制
     */
    private static List<ProviderLimitMechanism> builtins() {
        return List.of(
                new ManualLimitMechanism(),
                new DocLimitMechanism(),
                new HeaderLimitMechanism()
        );
    }
}

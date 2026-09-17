package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
* 主机远程操作 提供者注册中心
* <p>
* 启动时扫描全部 {@link HostRemoteProvider} Bean，按系统类型建索引；
* 部署/启停/实时日志/指标采集/守护探测等入口统一通过本注册中心解析实现，
* 从而做到「新增一种服务器系统只加一个实现类，上层零改动」。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
public class HostRemoteProviderRegistry {

    /** 系统类型 -> 实现索引 */
    private final Map<String, HostRemoteProvider> index = new ConcurrentHashMap<>();

    /** 兜底实现（处理未注册的系统类型） */
    private volatile HostRemoteProvider fallbackProvider;

    /**
    * 构造：注册全部远程操作提供者
    *
    * @param providers 提供者列表
    */
    @Autowired
    public HostRemoteProviderRegistry(List<HostRemoteProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[remote] 未发现 HostRemoteProvider 实现");
            return;
        }
        for (HostRemoteProvider provider : providers) {
            for (String osType : provider.osTypes()) {
                if (osType == null || osType.isBlank()) {
                    continue;
                }
                HostRemoteProvider exists = index.put(osType.trim().toUpperCase(Locale.ROOT), provider);
                if (exists != null) {
                    log.warn("[remote] 系统类型 {} 存在多个实现，后注册者生效: {} 覆盖 {}",
                            osType, provider.getClass().getSimpleName(), exists.getClass().getSimpleName());
                }
            }
            if (provider.fallback() && fallbackProvider == null) {
                fallbackProvider = provider;
            }
            log.info("[remote] 注册远程操作 Provider: osTypes={}, fallback={}, impl={}",
                    provider.osTypes(), provider.fallback(), provider.getClass().getSimpleName());
        }
    }

    /**
    * 按系统类型解析提供者
    *
    * @param osType 系统类型（如 LINUX/WINDOWS）
    * @return Optional，可能为空
    */
    public Optional<HostRemoteProvider> resolve(String osType) {
        if (osType == null || osType.isBlank()) {
            return Optional.ofNullable(fallbackProvider);
        }
        HostRemoteProvider provider = index.get(osType.trim().toUpperCase(Locale.ROOT));
        if (provider != null) {
            return Optional.of(provider);
        }
        if (fallbackProvider != null) {
            log.warn("[remote] 系统类型 {} 未注册专用实现，回退兜底实现 {}",
                    osType, fallbackProvider.getClass().getSimpleName());
            return Optional.of(fallbackProvider);
        }
        return Optional.empty();
    }

    /**
    * 按主机解析提供者
    *
    * @param host 主机
    * @return Optional，可能为空
    */
    public Optional<HostRemoteProvider> resolve(MsServerHost host) {
        if (host == null) {
            return Optional.empty();
        }
        return resolve(host.getServerHostOsType());
    }

    /**
    * 按主机解析提供者，解析不到时抛出带明确指引的异常
    * <p>
    * 与 {@link #resolve(MsServerHost)} 的区别：用于「必须有实现才能继续」的链路，
    * 报错信息直接指出该补哪种系统的实现类，避免部署时出现难以定位的空指针。
    * </p>
    *
    * @param host 主机
    * @return 提供者，永不为 null
    * @throws IllegalStateException 无任何可用实现时抛出
    */
    public HostRemoteProvider require(MsServerHost host) {
        return resolve(host).orElseThrow(() -> new IllegalStateException(
                "无可用的远程操作实现: osType=" + (host == null ? "null" : host.getServerHostOsType())
                        + "，请新增 HostRemoteProvider 实现并声明 osTypes()"));
    }

    /**
    * 按系统类型解析提供者，解析不到时抛出带明确指引的异常
    *
    * @param osType 系统类型
    * @return 提供者，永不为 null
    * @throws IllegalStateException 无任何可用实现时抛出
    */
    public HostRemoteProvider require(String osType) {
        return resolve(osType).orElseThrow(() -> new IllegalStateException(
                "无可用的远程操作实现: osType=" + osType
                        + "，请新增 HostRemoteProvider 实现并声明 osTypes()"));
    }

    /**
    * 解析系统类型对应的命令 shell 方言
    * <p>
    * 供「AI 生成启停/检测脚本」「高危命令审计」等入口使用：方言必须与
    * {@link HostRemoteProvider#exec} 的通道一致，否则生成出来的脚本执行不了。
    * </p>
    *
    * @param osType 系统类型（如 LINUX/WINDOWS），可为空
    * @return shell 方言，解析不到时按 POSIX 兜底
    */
    public HostShellDialect shellDialectOf(String osType) {
        return resolve(osType).map(HostRemoteProvider::shellDialect).orElse(HostShellDialect.POSIX);
    }

    /**
    * 已注册的系统类型与实现（诊断/运维排查用）
    *
    * @return 系统类型 -> 实现类名
    */
    public Map<String, String> describe() {
        Map<String, String> out = new LinkedHashMap<>();
        index.forEach((type, provider) -> out.put(type, provider.getClass().getSimpleName()));
        if (fallbackProvider != null) {
            out.put("*", fallbackProvider.getClass().getSimpleName() + "(fallback)");
        }
        return out;
    }
}

package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nginx 总览。
 *
 * <p>把「这个 nginx 现在怎么样」需要的所有信息聚合成一次调用返回，避免前端为拼总览
 * 串行发起六七个请求。</p>
 *
 * @param running         运行状态：RUNNING / STOPPED / UNKNOWN
 * @param masterPid       master 进程 PID，未运行时为 null
 * @param uptimeText      已运行时长可读文本，如 3 天 4 小时，未运行时为 null
 * @param configRoot      配置根路径
 * @param configPath      主配置文件路径（nginx -V 的 --conf-path）
 * @param version         版本与编译参数
 * @param status          stub_status 连接数统计，不可用时 available 为 false
 * @param configFileCount 配置文件数量
 * @param configTotalSize 配置文件总字节数
 * @param listenPorts     正在监听的端口列表
 * @param logFiles        日志文件清单
 * @param issues          巡检发现的问题（只读提示，不阻断操作）
 * @author CH
 * @since 4.0.0.42
 */
public record NginxOverviewVO(
        String running,
        Long masterPid,
        String uptimeText,
        String configRoot,
        String configPath,
        NginxVersionVO version,
        NginxStatusVO status,
        Integer configFileCount,
        Long configTotalSize,
        List<Integer> listenPorts,
        List<NginxLogFileVO> logFiles,
        List<String> issues
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param running         运行状态：RUNNING / STOPPED / UNKNOWN
     * @param masterPid       master 进程 PID，未运行时为 null
     * @param uptimeText      已运行时长可读文本，如 3 天 4 小时，未运行时为 null
     * @param configRoot      配置根路径
     * @param configPath      主配置文件路径（nginx -V 的 --conf-path）
     * @param version         版本与编译参数
     * @param status          stub_status 连接数统计，不可用时 available 为 false
     * @param configFileCount 配置文件数量
     * @param configTotalSize 配置文件总字节数
     * @param listenPorts     正在监听的端口列表
     * @param logFiles        日志文件清单
     * @param issues          巡检发现的问题（只读提示，不阻断操作）
     */
    public NginxOverviewVO {
        listenPorts = List.copyOf(Objects.requireNonNull(listenPorts, "listenPorts 不能为 null"));
        logFiles = List.copyOf(Objects.requireNonNull(logFiles, "logFiles 不能为 null"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues 不能为 null"));
    }
}

package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 自动部署配置
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.agent")
public class AgentDeployProperties {

    /**
     * agent 默认 HTTP 端口（主机未单独配置时使用）
    */
    public static final int DEFAULT_PORT = 19876;

    /**
     * agent.jar 路径。为空时按候选路径探测（工作目录 monitor-agent/agent.jar 等），
     * 全部落空则直接以明确错误终止部署，不再静默上传一个不存在或过期的包。
     */
    private String jarPath;

    /**
     * agent 包的平台下载地址（如 http://10.0.0.5:8094/ms/agent/package）。
     * <p>
     * 非空时安装/升级改为让目标机自行 HTTP 拉取，拉取失败再回退通道上传；
     * Windows 主机的 WinRM 通道受单条命令长度上限约束，大文件必须走这条路径。
     * </p>
     */
    private String jarUrl = "";

    /**
     * Linux 系统服务名（systemd unit）
    */
    private String linuxServiceName = "monitor-agent";

    /**
     * Windows 系统服务名（nssm）
    */
    private String windowsServiceName = "MonitorAgent";

    /**
     * 远端安装目录名（位于各系统的临时目录下）
    */
    private String remoteDirName = "monitor-agent";

    /**
     * agent 默认端口
    */
    private int port = DEFAULT_PORT;

    /**
     * 写入远端 agent.yml 的接口令牌；为空表示不启用 /exec 令牌校验。
     * 平台侧访问 agent 时会自动带上同值 token。
     */
    private String token = "";

    /**
     * 启动后等待 /health 就绪的秒数
    */
    private int healthWaitSeconds = 30;

    /**
     * 单次 agent HTTP 请求超时（秒）
    */
    private int requestTimeoutSeconds = 8;

    /**
     * 远端 java 可执行文件；留空则自动探测（command -v java / where java）
    */
    private String javaCommand = "";

    /**
     * 平台回写地址（写入 agent.yml 供 agent 主动上报预留使用）
    */
    private String reportUrl = "";

    /**
     * APM RuntimeAgent jar 的平台下载地址（如 https://.../utils-support-runtime-agent.jar）。
     * 为空时若未配置 {@link #apmJarPath}，后端将按候选路径探测本地 runtime-agent jar。
     */
    private String apmJarUrl = "";

    /**
     * 本地 APM RuntimeAgent jar 路径。优先于候选路径探测。
     */
    private String apmJarPath = "";

    /**
     * 远端存放 APM RuntimeAgent jar 的文件名。
     */
    private String apmRemoteName = "apm-agent.jar";

    /**
     * 注入到目标 JVM 的 javaagent options（不含 -javaagent:jar= 前缀）。
     * 例如 port=4567 或 port=4567,enabled=true。
     */
    private String apmOptions = "port=4567";

    /**
     * APM JavaAgent 注入状态远端探针文件（Linux profile.d）。
     */
    private String apmLinuxProfile = "/etc/profile.d/apm-javaagent.sh";

    /**
     * APM JavaAgent 注入状态远端探针文件（Windows 用户环境变量）。
     */
    private String apmWindowsEnvName = "JAVA_TOOL_OPTIONS";
}

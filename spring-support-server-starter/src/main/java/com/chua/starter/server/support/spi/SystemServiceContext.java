package com.chua.starter.server.support.spi;

import java.util.List;
import java.util.Map;

/**
 * 系统服务挂载上下文（{@link SystemServiceProvider} 的唯一输入）。
 *
 * <p>刻意不直接传 {@code MsServerProject} 实体：SPI 实现属于可独立演进的扩展点，
 * 不应被 MyBatis 实体契约绑定。新增平台实现只需依赖本 record。</p>
 *
 * <p>集合组件在紧凑构造器中做防御性拷贝并把 {@code null} 归一为空集合，
 * 避免实现方各自判空（调用方普遍不判空直接透传实体字段）。</p>
 *
 * @param projectName   项目名称
 * @param projectId     项目标识，用于生成默认服务名
 * @param projectType   项目类型（springboot/fatjar/git/separated/static/nginx/model，
 *                      仅用于展示与日志；行为差异一律由 {@code foregroundCommand} 等已解析字段承载）
 * @param mainEntry     主入口名称（jar 名或可执行文件名）
 * @param projectPath   项目地址（服务的工作目录，必须包含主入口）
 * @param logPath       日志地址
 * @param startScript   启动脚本
 * @param stopScript    停止脚本
 * @param statusScript  状态脚本，守护进程据此判定存活
 * @param env           环境变量键值
 * @param jvmArgs       JVM 参数（-jar 之前）
 * @param cmdArgs       应用参数
 * @param runAsUser     服务运行账户；为空表示由平台默认（systemd 为 root），
 *                      已知的登录账户会写入平台 unit 的 {@code User=} 以免进程以 root 运行
 * @param autostart     是否开机自启动
 * @param guardian      是否守护进程（异常退出自动拉起）
 * @param foregroundCommand 前台常驻命令（由项目类型 SPI 生成，服务管理器跟踪到的即业务进程）；
 *                          为空时平台实现按主入口回落到 {@code './入口' $APP_ARGS}
 * @author CH
 * @since 4.0.0.42
 */
public record SystemServiceContext(
        String projectName,
        Long projectId,
        String projectType,
        String mainEntry,
        String projectPath,
        String logPath,
        String startScript,
        String stopScript,
        String statusScript,
        Map<String, String> env,
        List<String> jvmArgs,
        List<String> cmdArgs,
        String runAsUser,
        boolean autostart,
        boolean guardian,
        String foregroundCommand) {

    /**
     * 紧凑构造器：集合组件拷贝并归一 null，避免实现方各自判空。
     */
    public SystemServiceContext {
        env = env == null ? Map.of() : Map.copyOf(env);
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        cmdArgs = cmdArgs == null ? List.of() : List.copyOf(cmdArgs);
        foregroundCommand = foregroundCommand == null ? "" : foregroundCommand.trim();
    }
}

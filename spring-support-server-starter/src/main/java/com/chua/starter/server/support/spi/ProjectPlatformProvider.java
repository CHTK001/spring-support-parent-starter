package com.chua.starter.server.support.spi;

import java.util.List;

/**
 * 发布模块「平台命令」提供者 SPI（按系统类型扩展）。
 *
 * <p>把 {@code ReleaseServiceImpl} 里所有与操作系统相关的<b>命令构造</b>收拢到本扩展点：
 * 备份/列备份/回滚、执行包装（建目录+切目录）、读取 PID、变量块与参数展开、SpringBoot 外部配置、
 * 升级投放、工具可用性检查与各平台转义规则。业务层只做编排与状态流转，
 * 不再出现 {@code tar}/{@code robocopy}/{@code systemctl}/{@code powershell} 之类的平台命令字面量，
 * 也不再出现 {@code windows ? A : B} 形式的命令二选一。</p>
 *
 * <p>新增平台只需实现本接口、标注 {@code @Spi} 并登记到
 * {@code META-INF/extensions/com.chua.starter.server.support.spi.ProjectPlatformProvider}；
 * 未登记平台会得到明确的「暂不支持」错误，而不是被静默当成 Linux 执行。</p>
 *
 * <p><b>实现约定：</b>返回的命令必须是「可直接交给目标机执行器」的完整片段；
 * 涉及路径/参数一律走本实现的转义规则，调用方不得自行拼接引号。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectPlatformProvider {

    /**
     * 支持的操作系统类型（与 {@code HostPlatforms} 取值域一致）。
     *
     * @return 系统类型标识（全大写）
     */
    String osType();

    /**
     * 生成备份命令：把项目目录打包/镜像到 backups/ 下，并清理超出保留数的旧备份。
     *
     * @param projectPath 项目地址
     * @param keep        保留的备份数量上限
     * @return 备份命令
     */
    String backup(String projectPath, int keep);

    /**
     * 生成列出备份的命令。
     *
     * @param projectPath 项目地址
     * @return 列出备份的命令
     */
    String listBackups(String projectPath);

    /**
     * 生成回滚命令：用指定备份（为空表示最新）覆盖当前项目目录。
     *
     * @param projectPath 项目地址
     * @param backupName  备份名；为空表示取最新
     * @return 回滚命令
     */
    String rollback(String projectPath, String backupName);

    /**
     * 把脚本包装为可在目标机执行的完整命令（建立并切换到项目目录后执行脚本）。
     *
     * @param projectPath 项目地址；为空时不切换目录
     * @param script      脚本内容
     * @return 包装后的命令
     */
    String wrap(String projectPath, String script);

    /**
     * 读取进程号命令（供启动/重启后回写卡片）。
     *
     * @return 读取 app.pid 的命令
     */
    String readPid();

    /**
     * 读取指定 PID 文件的命令（默认委托 {@link #readPid()}，兼容旧实现）。
     *
     * @param pidFile PID 文件相对路径（如 {@code app.pid}、{@code logs/nginx.pid}）
     * @return 读取命令
     */
    default String readPid(String pidFile) {
        return readPid();
    }

    /**
     * 生成变量块（env/jvm/cmd 结构化变量注入脚本用）。
     *
     * @param envNames  环境变量名
     * @param envValues 环境变量值（与名称一一对应）
     * @param jvmArgs   JVM 参数
     * @param cmdArgs   应用参数
     * @return 变量块脚本
     */
    String varsBlock(List<String> envNames, List<String> envValues, List<String> jvmArgs, List<String> cmdArgs);

    /**
     * JVM 参数占位符的展开字面量。
     *
     * @param jvmArgs JVM 参数
     * @return 展开字面量
     */
    String jvmArgsLiteral(List<String> jvmArgs);

    /**
     * 应用参数占位符的展开字面量。
     *
     * @param cmdArgs 应用参数
     * @return 展开字面量
     */
    String appArgsLiteral(List<String> cmdArgs);

    /**
     * 解析脚本中环境变量导出的正则（用于逆向提取已有环境变量）。
     *
     * @return 正则表达式
     */
    String envPattern();

    /**
     * 生成 SpringBoot 外部配置（config/application.properties）写入脚本。
     *
     * @param keys   配置键
     * @param values 配置值（与键一一对应）
     * @return 写入脚本
     */
    String springConfigScript(List<String> keys, List<String> values);

    /**
     * 生成解压命令：把升级包解到项目目录。
     *
     * @param projectPath 项目地址
     * @param packagePath 包路径（按扩展名选择解压方式）
     * @return 解压命令
     */
    String extract(String projectPath, String packagePath);

    /**
     * 生成投放命令：把升级包放到主入口位置（主入口为空时用包名）。
     *
     * @param projectPath 项目地址
     * @param packagePath 包路径
     * @param mainEntry   主入口名称
     * @return 投放命令
     */
    String place(String projectPath, String packagePath, String mainEntry);

    /**
     * 生成「重启 → 健康检查 → 失败回滚 → 二次检查」命令。
     *
     * @param projectPath   项目地址
     * @param restartScript 重启脚本（可为空，空则用 停止+启动 组合）
     * @param stopScript    停止脚本
     * @param startScript   启动脚本
     * @param statusScript  状态脚本
     * @param restart       是否为重启场景（false 时只做健康检查）
     * @return 包装后的命令
     */
    String guardedRestart(String projectPath, String restartScript, String stopScript,
                          String startScript, String statusScript, boolean restart);

    /**
     * 生成「重启 → 健康检查（就绪等待 + 端口探活 + 稳定窗口）→ 失败回滚 → 二次检查」命令。
     *
     * <p>与 6 参版本的区别是多了 {@code port}：给定端口时，健康检查除「状态脚本报告运行中」外
     * 还要求该端口处于监听状态，从而识别「进程活着但端口没绑上」这类状态脚本查不出的故障
     * （典型：SpringBoot 启动失败但仍驻留、或业务端口被占用后静默退化）。</p>
     *
     * <p>默认实现忽略 {@code port} 并委托给 6 参版本，便于既有实现平滑升级。</p>
     *
     * @param projectPath   项目地址
     * @param restartScript 重启脚本（可为空，空则用 停止+启动 组合）
     * @param stopScript    停止脚本
     * @param startScript   启动脚本
     * @param statusScript  状态脚本
     * @param restart       是否为重启场景（false 时只做健康检查）
     * @param port          业务端口；{@code <=0} 表示不探活
     * @return 包装后的命令
     */
    default String guardedRestart(String projectPath, String restartScript, String stopScript,
                                  String startScript, String statusScript, boolean restart, int port) {
        return guardedRestart(projectPath, restartScript, stopScript, startScript, statusScript, restart);
    }

    /**
     * 生成读取日志尾部的命令。
     *
     * @param logPath 日志文件路径
     * @param lines   读取行数
     * @return 读取命令
     */
    String tailLog(String logPath, int lines);

    /**
     * 生成「确保目录存在」命令（已存在时视为成功）。
     *
     * <p>典型场景：首次发布时日志目录尚不存在，而启动脚本把标准输出重定向到
     * {@code logs/app.log}。shell 在重定向阶段就失败、进程根本没起来，但脚本末尾的
     * {@code echo $! > app.pid} 仍会执行并返回 0，任务于是报「启动成功」而状态是死的。
     * 发布前先由平台命令补齐目录，才能让「新目录首次部署」与「已有目录升级」表现一致。</p>
     *
     * @param dir 目录绝对路径；调用方需保证非空
     * @return 建目录命令
     */
    String ensureDir(String dir);

    /**
     * 单引号/字符串转义（各平台规则不同）。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    String quote(String value);
}

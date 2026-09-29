package com.chua.starter.monitor.support.nginx.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Nginx 控制台的<strong>命令文本</strong>扩展点。
 *
 * <p>存在的理由：Nginx 控制台原先把命令文本硬编码在
 * {@code NginxManageServiceImpl} 里，全是 POSIX 语法（{@code systemctl} / {@code pgrep} /
 * {@code tail -n} / {@code ls -1t} / {@code test -f} / {@code find | while read}），
 * Windows 上一条都跑不了——不是「没测」，是结构上不可能跑。本扩展点把「同一件事在不同
 * 平台上怎么写」收敛成一组实现，新增平台只需提交一个实现类 + 注册文件，不必改调用方。</p>
 *
 * <p><strong>与通道的分工</strong>：本接口只负责<em>拼命令</em>，不负责<em>把命令送出去</em>。
 * 执行由 {@link NginxExecutor} 承担。这条分工与本仓库既有的
 * {@code HostOperations}（命令文本）/ {@code SystemInfoProvider}（通道）一致——
 * {@code SystemInfoProvider} 的类注释写得很明确：「命令文本不在通道实现里，也不在调用方，
 * 统一由平台子类给出」。</p>
 *
 * <p><strong>实现注意</strong>：所有返回值都是<em>可直接交给目标平台 shell 的完整命令行</em>，
 * 路径必须先经过 {@link #quote(String)}。不要在这里做 IO，也不要缓存。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(NginxCommandProvider.SPI_NAME)
public interface NginxCommandProvider {

    /**
     * 扩展点名称。
     */
    String SPI_NAME = "nginx-command-provider";

    /**
     * 本实现覆盖的平台是否匹配。
     *
     * <p>入参已由 {@code Platforms.normalize} 归一，直接与
     * {@code Platforms.WINDOWS} / {@code LINUX} / {@code MACOS} 比较。</p>
     *
     * @param platform 归一后的平台标识
     * @return 匹配返回 true
     */
    boolean supports(String platform);

    /**
     * 扩展别名，用于日志与排障。
     *
     * @return 别名，如 {@code nginx-posix}
     */
    String name();

    /**
     * 把任意字面量转成目标平台 shell 里的安全字面量。
     *
     * <p>这是本扩展点最容易被忽略、后果最严重的一个方法：POSIX 用单引号，而 Windows
     * {@code cmd} <strong>根本不把单引号当引号</strong>，同一个串在两边必须转义成不同形式，
     * 否则路径会带着引号一起被当成文件名。</p>
     *
     * @param value 原始字面量
     * @return 可安全拼接的字面量
     */
    String quote(String value);

    /**
     * 平台的<strong>自证命令</strong>：只在本平台可执行，输出用于判定平台。
     *
     * <p>刻意不做成「一条命令两边通吃」：那种写法要求两个 shell 的语法交集，
     * 而 POSIX 的 {@code 2>/dev/null} 在 Windows {@code cmd} 里会被当成重定向并
     * 因路径 {@code /dev/null} 不存在而报错，反而把真正的探测命令挡掉。所以改为
     * 路由器按 order 依次尝试各实现的自证命令，第一条能判定平台的就采纳。</p>
     *
     * @return 本平台的自证命令
     */
    String probePlatform();

    /**
     * 依据探测输出判定平台。
     *
     * @param probeOutput 探测命令的原始输出
     * @return 归一后的平台标识；不是本平台或无法判定时返回空串
     */
    String parsePlatform(String probeOutput);

    /**
     * 判断输出是否代表命令执行失败（各平台失败提示不同）。
     *
     * @param output 命令输出
     * @return 判定为失败返回 true
     */
    boolean looksLikeError(String output);

    /**
     * 服务控制命令（systemd 的 {@code systemctl} / Windows 的 {@code sc}）。
     *
     * @param unit   服务单元名
     * @param action 动作，如 {@code start} / {@code stop} / {@code restart}
     * @return 完整命令行
     */
    String serviceControl(String unit, String action);

    /**
     * 枚举可能托管 nginx 的服务单元。
     *
     * <p>用于「自动探测托管者」：POSIX 走 systemd 的 {@code ExecStart} 反查；Windows 没有
     * 等价物，返回空串表示该平台不支持此能力，调用方应跳过而不是当成失败。</p>
     *
     * @return 完整命令行；平台不支持返回空串
     */
    String serviceUnitOfProcess();

    /**
     * 查询某服务单元的主进程是否为指定 pid。
     *
     * @param unit 服务单元名
     * @return 完整命令行；平台不支持返回空串
     */
    String serviceMainPid(String unit);

    /**
     * 进程存活探测命令。
     *
     * @param confTail nginx 配置路径尾部（用于在同名进程里认出属于本实例的那个）
     * @return 完整命令行，输出 {@code ON} / {@code OFF}
     */
    String processAlive(String confTail);

    /**
     * 取进程主 pid 的命令。
     *
     * @param confTail nginx 配置路径尾部
     * @return 完整命令行
     */
    String processPid(String confTail);

    /**
     * 读文本文件。
     *
     * @param path 文件路径
     * @return 完整命令行
     */
    String readFile(String path);

    /**
     * 判断文件是否存在。
     *
     * @param path 文件路径
     * @return 完整命令行
     */
    String fileExists(String path);

    /**
     * 递归创建目录。
     *
     * @param dir 目录
     * @return 完整命令行
     */
    String makeDirs(String dir);

    /**
     * 复制文件（保留时间戳）。
     *
     * @param source      源
     * @param destination 目标
     * @return 完整命令行
     */
    String copyFile(String source, String destination);

    /**
     * 按修改时间倒序列出目录下匹配后缀的文件。
     *
     * @param dir    目录
     * @param suffix 文件名后缀，如 {@code .log}
     * @return 完整命令行
     */
    String listByModifiedDesc(String dir, String suffix);

    /**
     * 取文件末尾 N 行。
     *
     * @param path  文件路径
     * @param lines 行数
     * @return 完整命令行
     */
    String tailLines(String path, int lines);

    /**
     * 取文件字节数。
     *
     * @param path 文件路径
     * @return 完整命令行
     */
    String fileSize(String path);

    /**
     * 从指定字节偏移开始取文件内容（用于增量读取日志）。
     *
     * @param path  文件路径
     * @param start 起始字节（1 基）
     * @return 完整命令行
     */
    String bytesFrom(String path, long start);

    /**
     * 在目录下按文件名模式查找文件。
     *
     * @param root    起始目录
     * @param pattern 文件名模式
     * @return 完整命令行
     */
    String findFiles(String root, String pattern);

    /**
     * 探测可执行文件是否在 PATH 中。
     *
     * @param command 可执行文件名
     * @return 完整命令行
     */
    String which(String command);

    /**
     * 读取 nginx 编译参数。
     *
     * @param binary nginx 可执行文件（空则用默认 {@code nginx}）
     * @return 完整命令行
     */
    String versionDump(String binary);

    /**
     * 列出监听端口。
     *
     * @return 完整命令行
     */
    String listenRows();

    /**
     * 组装一条带 prefix / conf 的 nginx 命令。
     *
     * <p>所有 nginx 命令都显式带 {@code -p} 与 {@code -c}：nginx 按 prefix 解析相对路径，
     * 而 SSH/新进程的工作目录不可控（POSIX 是登录家目录，Windows 是 nginx.exe 所在盘符的
     * 当前目录），只写 {@code nginx -t} 会去错误的位置找配置。Windows 上路径还要用
     * {@code cd /d} 先切盘符 + 目录，否则跨盘符的相对路径仍然错。</p>
     *
     * @param binary nginx 可执行文件（空则用默认）
     * @param prefix 配置目录前缀，可为空
     * @param conf   配置文件路径，可为空
     * @param action 动作参数，如 {@code -t}、{@code -s reload}；启动传空串
     * @return 完整命令行
     */
    String nginxInvocation(String binary, String prefix, String conf, String action);
}

package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
   * 主机远程操作 提供者 SPI（按系统类型路由）
   * <p>
   * 与 {@link HostDetectProvider} 同包同范式：实现类标注 {@code @Spi} 并登记到
   * {@code META-INF/extensions} 由扩展点发现，
   * {@link HostRemoteProviderRegistry} 按 {@link #osTypes()} 建索引，
   * 调用方只需给出主机，无需关心底层是 SSH/SFTP 还是 WinRM/PowerShell。
   * </p>
   *
   * <p>
   * 之所以必须做传输层抽象而不是只抽象命令串：Windows 默认不提供 SSH，
   * 把 cmd/PowerShell 命令塞进 SSH 通道在真实 Windows 主机上根本跑不通。
   * 因此 Linux 系走 SSH/SFTP，Windows 走 WinRM，各自封装完整语义。
   * </p>
   *
   * <p>
   * 新增系统类型：追加实现类并声明 {@link #osTypes()} 即可，上层零改动。
   * </p>
   *
   * @author CH
   * @since 4.0.0.42
   */
public interface HostRemoteProvider {

      /**
       * 主系统类型（与 {@code ms_server_host.server_host_os_type} 对齐，全大写）
       *
       * @return 系统类型标识
       */
    String osType();

      /**
       * 该实现负责的全部系统类型
       * <p>
       * 默认仅 {@link #osType()}；POSIX 系（Linux/macOS/FreeBSD/AIX/Solaris）命令语义一致，
       * 可由同一实现声明多个类型，避免重复代码。
       * </p>
       *
       * @return 系统类型列表
       */
    default List<String> osTypes() {
        return List.of(osType());
    }

      /**
       * 是否作为「未注册系统类型」的兜底实现
       * <p>
       * 兜底实现只在没有任何实现声明该类型时被选用，且注册中心会打告警日志，
       * 便于发现「主机系统类型填错 / 新系统未适配」。
       * </p>
       *
       * @return true 表示可兜底
       */
    default boolean fallback() {
        return false;
    }

      /**
       * 执行命令并返回退出码与输出
       *
       * @param host           目标主机
       * @param credential     解密后的凭据
       * @param command        命令文本（POSIX shell 语法 / Windows PowerShell 语法）
       * @param timeoutSeconds 超时秒数，&lt;=0 时取实现默认值
       * @return 执行结果（不因退出码非 0 而抛异常，由调用方判定）
       * @throws Exception 连接失败或执行超时
       */
    RemoteExecResult exec(MsServerHost host, SshCredential credential, String command, int timeoutSeconds) throws Exception;

      /**
       * 上传本地文件到远端绝对路径（覆盖）
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param remotePath 远端目标文件绝对路径
       * @param localFile  本地源文件
       * @return 执行结果
       * @throws Exception 上传失败
       */
    RemoteExecResult upload(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception;

      /**
       * 确保远端目录存在（递归创建，已存在不报错）
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param path       远端目录绝对路径
       * @return 执行结果
       * @throws Exception 创建失败
       */
    RemoteExecResult mkdirs(MsServerHost host, SshCredential credential, String path) throws Exception;

      /**
       * 清空目录内容但保留目录本身
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param dir        远端目录绝对路径
       * @return 执行结果
       * @throws Exception 清理失败
       */
    RemoteExecResult clearDir(MsServerHost host, SshCredential credential, String dir) throws Exception;

      /**
       * 解压归档文件到目标目录
       *
       * @param host        目标主机
       * @param credential  凭据
       * @param archivePath 远端归档文件绝对路径
       * @param fileName    归档文件名（用于判定 zip/tar 等格式）
       * @param dir         解压目标目录
       * @return 执行结果
       * @throws Exception 解压失败
       */
    RemoteExecResult extract(MsServerHost host, SshCredential credential, String archivePath, String fileName, String dir) throws Exception;

      /**
       * 删除远端文件或目录（不存在视为成功）
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param path       远端路径
       * @return 执行结果
       * @throws Exception 删除失败
       */
    RemoteExecResult remove(MsServerHost host, SshCredential credential, String path) throws Exception;

      /**
       * 查询远端文件字节大小（实时日志游标定位用）
       * <p>
       * 查询失败或文件不存在返回 -1，由调用方决定兜底策略，不抛异常。
       * </p>
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param file       远端文件绝对路径
       * @return 字节数；不可用时为 -1
       */
    long fileSize(MsServerHost host, SshCredential credential, String file);

      /**
       * 读取文件尾部（实时日志）
       *
       * @param host       目标主机
       * @param credential 凭据
       * @param file       远端文件绝对路径
       * @param cursor     上次读取的字节偏移，null 表示取末尾若干行
       * @param lines      首帧拉取行数
       * @return 日志分片（内容 + 下次字节偏移）
       * @throws Exception 读取失败
       */
    RemoteLogChunk tail(MsServerHost host, SshCredential credential, String file, Long cursor, int lines) throws Exception;

      /**
       * 健康探测
       * <p>
       * 各系统类型的探测命令差异很大，由实现自行落地；上层只声明探什么。
       * 退出码 0 视为探测通过。
       * </p>
       *
       * @param host           目标主机
       * @param credential     凭据
       * @param type           探测类型（端口/HTTP/进程）
       * @param target         探测目标（端口号 / host:port / 进程名）
       * @param timeoutSeconds 超时秒数，&lt;=0 时取实现默认值
       * @return 执行结果（退出码 0 表示探测通过）
       * @throws Exception 连接失败
       */
    RemoteExecResult healthCheck(MsServerHost host, SshCredential credential, HostHealthType type, String target, int timeoutSeconds
        ) throws Exception;

      /**
       * 采集单个指标点
       * <p>
       * 采集命令与解析方式由实现自行落地（POSIX 用 pgrep/ps/ss/curl，Windows 用
       * Get-CimInstance/Get-Process/Get-NetTCPConnection/Invoke-WebRequest）。
       * 上层只声明「采什么」，不再按系统类型拼命令。
       * </p>
       * <p>
       * <b>返回值约定</b>：目标进程/端口不存在属于正常业务态，实现返回 -1（沿用既有告警口径，
       * 便于规则按「-1=无进程」判定）；输出无法解析或该指标在当前系统不可用时返回
       * {@link Double#NaN}，调用方跳过该指标点；仅在连接失败等基础设施异常时抛异常。
       * </p>
       *
       * @param host           目标主机
       * @param credential     凭据
       * @param type           指标类型
       * @param target         采集目标（进程关键字 / 端口号 / host:port）
       * @param timeoutSeconds 超时秒数，&lt;=0 时取实现默认值
       * @return 指标数值；无数据时为 -1，不可用时为 {@link Double#NaN}
       * @throws Exception 连接失败或执行超时
       */
    double metric(MsServerHost host, SshCredential credential, HostMetricType type, String target, int timeoutSeconds) throws Exception;

      /**
       * 该实现执行命令所用的 shell 方言
       * <p>
       * 供上层决定「生成什么脚本 / 审计什么高危命令」：生成出来的脚本必须能在
       * {@link #exec} 的通道里直接执行。默认 POSIX（sh/bash 语法）。
       * </p>
       *
       * @return shell 方言
       */
    default HostShellDialect shellDialect() {
        return HostShellDialect.POSIX;
    }

      /**
       * 远端临时目录（部署包中转用）
       *
       * @return 临时目录绝对路径（POSIX 如 /tmp，Windows 如 C:\Windows\Temp）
       */
    String tempDir();

      /**
       * 按目标系统规则拼接远端路径
       *
       * @param parts 路径片段
       * @return 拼接后的路径
       */
    String join(String... parts);

      /**
       * 按目标系统规则转义路径（供脚本模板 {{dir}} 替换，防止空格/特殊字符导致命令注入）
       *
       * @param path 原始路径
       * @return 可安全嵌入命令的路径字面量
       */
    String quotePath(String path);

      /**
       * 是否支持该主机
       *
       * @param host 主机
       * @return 主机系统类型是否在本实现声明的范围内
       */
    default boolean supports(MsServerHost host) {
        if (host == null || host.getServerHostOsType() == null) {
            return false;
        }
        String os = host.getServerHostOsType().trim().toUpperCase(Locale.ROOT);
        for (String type : osTypes()) {
            if (type != null && type.equalsIgnoreCase(os)) {
                return true;
            }
        }
        return false;
    }
}

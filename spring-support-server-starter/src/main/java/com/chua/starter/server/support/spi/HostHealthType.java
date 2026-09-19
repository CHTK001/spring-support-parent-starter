package com.chua.starter.server.support.spi;

/**
 * 主机健康探测类型
 * <p>
 * 供 {@link HostRemoteProvider#healthCheck} 使用：不同系统类型的探测命令差异很大
 * （POSIX 用 ss/curl/pgrep，Windows 用 Get-NetTCPConnection/Invoke-WebRequest/Get-Process），
 * 由各提供者实现自行落地，上层只声明「探什么」。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum HostHealthType {

    /**
     * 端口监听探测：target 为端口号
    */
    PORT("PORT", "端口"),

    /**
     * HTTP 探测：target 为 host:port 或完整 URL
     */
    HTTP("HTTP", "HTTP"),

    /**
     * 进程探测：target 为进程名或命令行关键字
    */
    PROCESS("PROCESS", "进程");

    /**
     * 编码
    */
    private final String code;

    /**
     * 中文描述
    */
    private final String desc;

    /**
     * 构造
     *
     * @param code 编码
     * @param desc 描述
     */
    HostHealthType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 编码
     *
     * @return 编码
     */
    public String code() {
        return code;
    }

    /**
     * 中文描述
     *
     * @return 描述
     */
    public String desc() {
        return desc;
    }

    /**
     * 按编码解析（兼容大小写与未知值）
     *
     * @param code 编码
     * @return 枚举；无法识别时返回 {@link #PORT}
     */
    public static HostHealthType of(String code) {
        if (code == null || code.isBlank()) {
            return PORT;
        }
        for (HostHealthType value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return PORT;
    }
}

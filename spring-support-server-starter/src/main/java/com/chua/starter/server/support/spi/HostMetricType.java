package com.chua.starter.server.support.spi;

/**
 * 主机指标采集类型
 * <p>
 * 供 {@link HostRemoteProvider#metric} 使用：不同系统类型的采集命令差异很大
 * （POSIX 用 pgrep/ps/ss/curl，Windows 用 Get-CimInstance/Get-Process/Get-NetTCPConnection/Invoke-WebRequest），
 * 由各提供者实现自行落地，上层只声明「采什么」。
 * </p>
 * <p>
 * 与 {@link HostHealthType} 的分工：健康探测只关心「通/不通」，指标采集要拿到具体数值。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum HostMetricType {

    /**
     * 进程常驻内存（RSS）合计，单位 MB；target 为进程名或命令行关键字
     */
    PROCESS_MEM_MB("PROCESS_MEM_MB", "进程内存(MB)"),

    /**
     * 进程 CPU 占用合计，单位 %；target 为进程名或命令行关键字
     */
    PROCESS_CPU("PROCESS_CPU", "进程CPU(%)"),

    /**
     * 端口是否监听，target 为端口号；1 监听 / 0 未监听
     */
    PORT_ALIVE("PORT_ALIVE", "端口存活"),

    /**
     * HTTP 状态码，target 为 host:port 或完整 URL；探测失败返回 0
     */
    HTTP_CODE("HTTP_CODE", "HTTP状态码");

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
     * @param code 编码
     * @param desc 描述
     * @return 返回值
    */
    HostMetricType(String code, String desc) {
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
     * @return 枚举；无法识别时返回 null，由调用方决定是否跳过
     */
    public static HostMetricType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (HostMetricType value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}

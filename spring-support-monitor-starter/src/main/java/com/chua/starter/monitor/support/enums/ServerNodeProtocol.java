package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
* 服务管理 —— 协议服务器类型枚举。
*
* <p>枚举值对应 common-starter {@code Server} 体系可创建的协议服务器：
* {@code ServerBuilder.type("jdk"/"nio"/"aio")} 创建 HTTP 服务器，
* {@code TcpProxyServer}/{@code HttpProxyServer} 创建端口/HTTP 代理，
* {@code SipServer} 创建 SIP 隧道服务器（无 @Spi，需直接构造）。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Getter
public enum ServerNodeProtocol {

    /**
    * HTTP 服务器（可挂 服务端过滤器 插件，例如 文件storage服务端过滤器 变成文件服务器）
    * @param "HTTP" "HTTP"
    * @param "http" "http"
    * @param 18080 18080
    * @param "jdk" "jdk"
    * @param false false
    * @param 插件 插件
    * @return HTTP的结果
    */
    HTTP("HTTP", "http", 18080, "jdk", false, "HTTP 服务器(可挂 ServerFilter 插件)"),

    /**
    * HTTP 反向代理服务器（需要 backend主机/backend端口 后端地址）
    * @param "HTTP_PROXY" "HTTP_代理"
    * @param "http-proxy" "http-代理"
    * @param 18081 18081
    * @param "jdk" "jdk"
    * @param true true
    * @param 反向代理服务器" 反向代理服务器"
    * @return http代理的结果
    */
    HTTP_PROXY("HTTP_PROXY", "http-proxy", 18081, "jdk", true, "HTTP 反向代理服务器"),

    /**
    * TCP 端口代理服务器（需要 backend主机/backend端口 后端地址）
    * @param "TCP_PROXY" "TCP_代理"
    * @param "tcp-proxy" "tcp-代理"
    * @param 18082 18082
    * @param "jdk" "jdk"
    * @param true true
    * @param 端口代理服务器" 端口代理服务器"
    * @return tcp代理的结果
    */
    TCP_PROXY("TCP_PROXY", "tcp-proxy", 18082, "jdk", true, "TCP 端口代理服务器"),

    /**
    * SIP 隧道服务器（单端口信令 + 数据平面，需要 令牌 认证）
    * @param "SIP" "SIP"
    * @param "sip" "sip"
    * @param 19460 19460
    * @param "sip" "sip"
    * @param false false
    * @param 隧道服务器" 隧道服务器"
    * @return SIP的结果
    */
    SIP("SIP", "sip", 19460, "sip", false, "SIP 隧道服务器");

    /**
    * 存储编码
    */
    private final String code;
    /**
    * 别名（请求可传）
    */
    private final String alias;
    /**
    * 默认端口
    */
    private final int defaultPort;
    /**
    * 默认引擎
    */
    private final String defaultEngine;
    /**
    * 是否要求后端地址
    */
    private final boolean requiresBackend;
    /**
    * 描述
    */
    private final String desc;

    /**
    * 构造方法
    *
    * @param code            编码
    * @param alias           别名
    * @param defaultPort     默认端口
    * @param defaultEngine   默认引擎
    * @param requiresBackend 是否要求后端地址
    * @param desc            描述
    */
    ServerNodeProtocol(String code, String alias, int defaultPort, String defaultEngine,
                       boolean requiresBackend, String desc) {
        this.code = code;
        this.alias = alias;
        this.defaultPort = defaultPort;
        this.defaultEngine = defaultEngine;
        this.requiresBackend = requiresBackend;
        this.desc = desc;
    }

    /**
    * 按编码或别名解析枚举。
    *
    * <p>兼容 {@code http / HTTP}、{@code http-proxy / http_proxy / HTTP_PROXY}、
    * {@code tcp-proxy / tcp_proxy}、{@code sip} 等写法。</p>
    *
    * @param code 编码或别名
    * @return 枚举值，未匹配返回 空
    */
    public static ServerNodeProtocol of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = normalize(code);
        for (ServerNodeProtocol value : values()) {
            if (normalize(value.code).equals(normalized) || normalize(value.alias).equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    /**
    * 归一化：去空白、统一小写、连字符与下划线等价。
    *
    * @param value 原始值
    * @return 归一化结果
    */
    private static String normalize(String value) {
        return value.trim().toLowerCase().replace("-", "_");
    }
}

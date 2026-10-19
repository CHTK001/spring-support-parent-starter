package com.chua.starter.monitor.support.node;

import java.util.Map;

/**
 * 在线节点视图（report 客户端注册信息）。
 *
 * @param clientId        会话标识（sync 注册标识）
 * @param applicationName 应用名
 * @param activeProfiles  激活环境
 * @param hostname        主机名
 * @param ipAddress       IP 地址
 * @param port            服务端口
 * @param contextPath     上下文路径
 * @param serviceUrl      服务地址
 * @param osName          操作系统
 * @param javaVersion     Java 版本
 * @param pid             进程号
 * @param startTime       启动时间（毫秒）
 * @param registerTime    注册时间（毫秒）
 * @param lastHeartbeat   最后心跳（毫秒）
 * @param uptime          运行时长（毫秒）
 * @param online          是否在线
 * @param metadata        扩展信息
 * @author CH
 * @since 4.0.0.42
 */
public record MonitorNodeVO(String clientId,
                            String applicationName,
                            String activeProfiles,
                            String hostname,
                            String ipAddress,
                            Integer port,
                            String contextPath,
                            String serviceUrl,
                            String osName,
                            String javaVersion,
                            Long pid,
                            Long startTime,
                            Long registerTime,
                            Long lastHeartbeat,
                            Long uptime,
                            Boolean online,
                            Map<String, Object> metadata) {
}

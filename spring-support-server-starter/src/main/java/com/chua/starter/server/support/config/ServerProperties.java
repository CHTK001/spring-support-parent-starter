package com.chua.starter.server.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务器资产模块配置属性
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "server.support")
public class ServerProperties {

    /**
     * 是否启用服务器资产模块
     */
    private boolean enabled = true;

    /**
     * guacd 配置
     */
    private Guacd guacd = new Guacd();

    /**
     * 凭据加密密钥（生产环境务必通过配置中心或环境变量注入）
     */
    private String credentialKey = "server-support-default-credential-key";

    /**
     * 远程终端会话空闲超时（分钟），超时后由清理任务关闭
     */
    private int sessionIdleMinutes = 30;

    /**
     * 批量执行最大并发主机数
     */
    private int execConcurrency = 16;

    /**
     * guacd 连接配置
     */
    @Data
    public static class Guacd {

        /**
         * guacd 主机地址
         */
        private String host = "127.0.0.1";

        /**
         * guacd 端口
         */
        private int port = 4822;

        /**
         * guacd-client 网关地址（浏览器侧 guacamole 客户端回连后端 WS 隧道的 host:port；
         * 前端与后端同域部署时置空即可，页面自动使用 location.host）
         */
        private String clientHost = "127.0.0.1:8082";

        /**
         * RDP 磁盘映射挂载根目录（enable-drive 时使用）
         */
        private String drivePath = "/opt/spring-support/drive";

        /**
         * 屏幕录制文件输出目录
         */
        private String recordingPath = "/opt/spring-support/recordings";
    }
}

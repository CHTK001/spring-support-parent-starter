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
    * 服务信息面板上"服务器资产"模块的展示旗标。
    * <p>
    * 注意：这不是模块开关。本模块没有 {@code @ConditionalOnProperty}，
    * 置 false 不会卸载任何 Bean；monitor/project 等模块直接注入本模块服务，
    * 真要停用只能移除依赖。
    * </p>
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
    * @author CH
    * @since 4.0.0
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
        * guacd-客户端 网关地址（浏览器侧 guacamole 客户端回连后端 WS 隧道的 主机:端口；
        * 前端与后端同域部署时置空即可，页面自动使用 位置.主机）
        */
        private String clientHost = "127.0.0.1:8082";

        /**
        * RDP 磁盘映射挂载根目录（enable-Drive 时使用）
        */
        private String drivePath = "/opt/spring-support/drive";

        /**
        * 屏幕录制文件输出目录
        */
        private String recordingPath = "/opt/spring-support/recordings";
    }
}

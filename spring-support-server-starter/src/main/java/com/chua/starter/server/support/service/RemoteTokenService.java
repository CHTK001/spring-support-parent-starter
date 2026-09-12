package com.chua.starter.server.support.service;

import com.chua.guacamole.support.client.GuacamoleClient;
import com.chua.starter.server.support.config.ServerProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
* 远控短令牌服务（guacamole客户端 单例封装）
*
* <p>基于 utils-support-guacamole-starter 的 {@link GuacamoleClient} 短链能力：
* 连接参数（目标机地址/端口/凭据）由后端登记进 令牌 登记表，
* 浏览器 URL/WS 上只携带无语义的随机 令牌——服务器信息不出后端。</p>
*
* <p>guacd 地址仅作为 GuacamoleClient 的属性存储（读配置表，
* 运行时可在系统配置页修改）；实际与 guacd 的握手仍由
* {@code GuacdConnectionFactory} 在 WS 桥接时完成，浏览器永远不直连 guacd。</p>
* @author CH
* @since 4.0.0
 */
@Slf4j
@Service
public class RemoteTokenService {

    /** 配置键：web 前端地址（令牌 会话 URL 的 主机 部分，供 iframe/跳转使用；空=不生成 URL） */
    private static final String KEY_WEB_HOST = "guacd.client.host";

    /** 配置键：web 前端端口 */
    private static final String KEY_WEB_PORT = "guacd.client.port";

    /** 配置键：短令牌有效期（分钟） */
    private static final String KEY_TOKEN_TTL_MINUTES = "guacd.token.ttl-minutes";

    /** 默认短令牌有效期（分钟） */
    private static final int DEFAULT_TOKEN_TTL_MINUTES = 60;

    /** guacamole客户端 单例（令牌 登记表必须全局唯一，签发与解析才对得上） */
    @Getter
    private GuacamoleClient client; // 客户端

    /** 服务属性（guacd 地址回落值） */
    private final ServerProperties properties;

    /** 系统配置服务（guacd 地址/令牌 TTL 动态化——配置表优先） */
    private final ServerConfigService configService;

    /**
    * 构造远控短令牌服务
    *
    * @param properties     服务属性
    * @param configService  系统配置服务
     */
    public RemoteTokenService(ServerProperties properties, ServerConfigService configService) {
        this.properties = properties;
        this.configService = configService;
        this.client = GuacamoleClient.create();
    }

    /**
    * 重建客户端（guacd 地址等基础配置变更后调用；注意旧 令牌 全部失效）
     */
    public void resetClient() {
        synchronized (this) {
            log.info("[remote-token] 重建 GuacamoleClient（旧 token 失效）");
            this.client = GuacamoleClient.create();
        }
    }

    /**
    * 签发远控短令牌（登记连接参数，令牌 不含任何服务器信息）
    *
    * @param protocol 协议（rdp/vnc/ssh/telnet）
    * @param host     目标主机地址
    * @param port     目标主机端口（空=协议默认端口）
    * @param username 登录用户名（可 空）
    * @param password 登录密码（可 空）
    * @return 短令牌（随机串）
     */
    public String issueToken(String protocol, String host, Integer port, String username, String password) {
        var op = client.issue().protocol(protocol).host(host);
        if (port != null) {
            op.port(port);
        }
        if (username != null && !username.isBlank()) {
            op.username(username);
        }
        if (password != null && !password.isBlank()) {
            op.password(password);
        }
        String token = op.issue();
        log.info("[remote-token] 签发令牌 protocol={} host={}:{} user={}", protocol, host, port, username);
        return token;
    }

    /**
    * 解析短令牌对应的连接参数（令牌 过期/未知时为空）
    *
    * @param token 短令牌
    * @return 连接参数集
     */
    public Optional<GuacamoleClient.RemoteSpec> resolve(String token) {
        return client.resolve(token);
    }

    /**
    * 生成会话 URL（指向 guacd 客户端 web 前端，fragment 仅含 令牌）
    *
    * <p>web 前端地址取配置表 {@code guacd.client.host}/{@code guacd.client.port}；
    * 未配置时返回 空（前端走 WS 直连隧道，无需会话 URL）。</p>
    *
    * @param token 短令牌
    * @return 形如 {@code http://host:port/guacamole/#token=xxx}；未配置 web 前端时为 空
     */
    public String buildSessionUrl(String token) {
        String host = configService.getString(KEY_WEB_HOST, "");
        if (host == null || host.isBlank()) {
            return null;
        }
        int port = configService.getInt(KEY_WEB_PORT, 80);
        int ttl = configService.getInt(KEY_TOKEN_TTL_MINUTES, DEFAULT_TOKEN_TTL_MINUTES);
        var client = GuacamoleClient.builder()
                .guacdHost(currentGuacdHost()).guacdPort(currentGuacdPort())
                .webHost(host).webPort(port)
                .tokenTtl(Duration.ofMinutes(ttl))
                .build();
        return client.url(token);
    }

    /**
    * 当前生效的 guacd 地址（配置表优先，回落 application.yml）
    * @return 当前guacd主机的结果
     */
    public String currentGuacdHost() {
        return configService.getString("guacd.host", properties.getGuacd().getHost());
    }

    /**
    * 当前生效的 guacd 端口
    * @return 当前guacd端口的结果
     */
    public int currentGuacdPort() {
        return configService.getInt("guacd.port", properties.getGuacd().getPort());
    }

    /**
    * 当前短令牌有效期（分钟，配置表优先）
    * @return 令牌ttlminutes的结果
     */
    public int tokenTtlMinutes() {
        return configService.getInt(KEY_TOKEN_TTL_MINUTES, DEFAULT_TOKEN_TTL_MINUTES);
    }
}

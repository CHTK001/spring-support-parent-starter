package com.chua.shell.support.configuration;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.crypto.decode.AesDecode;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.matcher.AntPathMatcher;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.protocol.client.ClientOption;
import com.chua.common.support.shell.BaseShell;
import com.chua.shell.support.properties.ShellProperties;
import com.chua.sshd.support.client.SshClient;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.chua.shell.support.configuration.ShellWebSocketConfiguration.ADDRESS;
import static com.chua.shell.support.configuration.ShellWebSocketHandler.sendText;

/**
 * ssh网络套接字hanlder
 *
 * @author CH
 * @since 2023/09/19
 */
@Slf4j
@ServerEndpoint(value = "/channel/ssh/{info}", configurator = ShellWebSocketConfiguration.class)
public class SshWebSocketHandler {
    private static final ConcurrentHashMap<Session, SshClient> HANDLER_ITEM_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private final String prompt = "$ ";

    @Resource
    private ShellProperties shellProperties = SpringBeanUtils.getBean(ShellProperties.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BaseShell shell = ShellWebSocketConfiguration.shell;

    private static final AtomicInteger COUNT = new AtomicInteger(0);

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("info") String info) throws Exception {
        if(!check(session)) {
            sendText(session, "@auth 无权限访问");
            session.close();
            return;
        }
        int cnt = COUNT.incrementAndGet();
        log.info("有连接加入，当前连接数为：{}", cnt);

        AesDecode aesDecode = new AesDecode();
        byte[] decode = aesDecode.decode(info, DateTime.now().toString("yyyyMMdd"));
        JsonObject jsonObject = Json.fromJson(decode, JsonObject.class);
        ClientOption clientOption = ClientOption.newBuilder().username(jsonObject.getString("username"))
                        .password(jsonObject.getString("password"));
        SshClient sshClient = new SshClient(clientOption);
        sshClient.connect(jsonObject.getString("ip") + ":" + jsonObject.getIntValue("port", 22));
        sshClient.addListener(session.getId(), s -> sendText(session, s));
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session, sshClient);
    }

    /**
     * 检查
     *
     * @param session 一场
     * @return boolean
     */
    private boolean check(Session session) {
        Object o = session.getUserProperties().get(ADDRESS);
        if(o == null) {
            return false;
        }

        String ip = o.toString();
        if(NetUtils.getLocalHost().equals(ip) || NetUtils.LOCAL_HOST.equals(ip)) {
            return true;
        }

        for (String ipPass : shellProperties.getIpPass()) {
            if(ipPass.contains(CommonConstant.SYMBOL_ASTERISK)) {
                if(AntPathMatcher.INSTANCE.match(ipPass, ip)) {
                    return true;
                }
                continue;
            }

            if(ipPass.equals(ip)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        SshClient sshClient = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session);
        try {
            sshClient.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        HANDLER_ITEM_CONCURRENT_HASH_MAP.remove(session);
        int cnt = COUNT.decrementAndGet();
        log.info("有连接关闭，当前连接数为：{}", cnt);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        SshClient sshClient = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session);
        if (Strings.isNullOrEmpty(message)) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("来自客户端的消息：{}", message);
        }
        sshClient.send(message);
    }

    /**
     * 出现错误
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}", error.getMessage(), session.getId());
        error.printStackTrace();
    }
}

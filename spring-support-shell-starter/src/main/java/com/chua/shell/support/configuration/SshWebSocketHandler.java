package com.chua.shell.support.configuration;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.matcher.AntPathMatcher;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.protocol.client.ClientOption;
import com.chua.common.support.shell.BaseShell;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
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
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.chua.shell.support.configuration.ShellWebSocketConfiguration.ADDRESS;
import static com.chua.shell.support.configuration.ShellWebSocketHandler.sendText;

/**
 * <pre>
 * +---------+     http     +--------+    ssh    +-----------+
 * | browser | <==========> | webssh | <=======> | ssh server|
 * +---------+   websocket  +--------+    ssh    +-----------+
 * </pre>
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

        info = StringUtils.utf8Str(Base64.getDecoder().decode(info));
        String key = DateTime.now().toString("yyyyMMdd");
        JSONObject jsonObject = Json.fromJson(DigestUtils.aesDecrypt(info, key + key), JSONObject.class);
        String password = jsonObject.getString("password");
        if(StringUtils.isEmpty(password)) {
            sendText(session, "@auth 密码不能为空");
            session.close();
            return;
        }


        ClientOption clientOption = ClientOption.newBuilder().username(jsonObject.getString("username")).password(password);
        SshClient sshClient = new SshClient(clientOption);
        sshClient.addListener(session.getId(), s -> sendText(session, s));
        sshClient.connect(jsonObject.getString("ip") + ":" + jsonObject.getIntValue("port", 22));
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
        if (null == sshClient) {
            return;
        }
        try {
            sshClient.close();
        } catch (Exception ignored) {
        }
        HANDLER_ITEM_CONCURRENT_HASH_MAP.remove(session);
        try {
            session.close();
        } catch (IOException ignored) {
        }
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

        if (null != sshClient && sshClient.isConnect()) {
            sshClient.send(message);
        }
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

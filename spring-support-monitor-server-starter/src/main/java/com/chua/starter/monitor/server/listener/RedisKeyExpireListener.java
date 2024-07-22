package com.chua.starter.monitor.server.listener;

import com.chua.common.support.json.Json;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.redis.support.listener.RedisListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.Topic;

import java.util.Collection;
import java.util.Collections;

import static com.chua.starter.monitor.server.constant.MonitorConstant.HEART;

/**
 * redis密钥过期侦听器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */

@Configuration
@RequiredArgsConstructor
public class RedisKeyExpireListener implements RedisListener {

    private final SocketSessionTemplate socketSessionTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        String string = new String(message.getChannel());
        if(key.startsWith(HEART) && string.endsWith(":expire")) {
            try {
                stringRedisTemplate.delete(key + ":SERVER");
                MonitorRequest request = new MonitorRequest();
                String substring = key.substring(HEART.length());
                String[] split = substring.split(":");
                request.setAppName(split[0]);
                String[] split1 = split[1].split("_");
                request.setServerHost(split1[0]);
                request.setServerPort(split1[1]);
                socketSessionTemplate.send(":offline", Json.toJson(request));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public Collection<Topic> getTopics() {
        return Collections.singletonList(new PatternTopic("__keyevent@*"));
    }
}

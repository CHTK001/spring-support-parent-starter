package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.redis.support.oshi.RedisReport;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class RedisAdaptor implements Adaptor<RedisReport> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(RedisReport redisReport) {
        socketSessionTemplate.send("redis", Json.toJson(redisReport));
    }

    @Override
    public Class<RedisReport> getType() {
        return RedisReport.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

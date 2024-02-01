package com.chua.starter.monitor.server.factory;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.constant.MonitorConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * 监控服务器工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Service
public class MonitorServerFactory implements MonitorConstant {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取心跳数据
     * @return 心跳数据的映射表，以应用名称为键，心跳数据列表为值
     */
    public List<MonitorRequest> getHeart(String appName) {
        // 获取所有的心跳数据键
        Set<String> keys = stringRedisTemplate.keys(HEART + appName);
        Map<String, List<MonitorRequest>> stringListMap = create(keys);
        if(stringListMap.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(stringListMap.get(appName));
    }

    private Map<String, List<MonitorRequest>> create(Set<String> keys) {
        if(CollectionUtils.isEmpty(keys)) {
            return Collections.emptyMap();
        }
        // 创建一个以应用名称为键，心跳数据列表为值的映射表
        Map<String, List<MonitorRequest>> rs = new HashMap<>(keys.size());
        // 遍历所有的心跳数据键
        for (String key : keys) {
            // 从Redis中获取对应键的心跳数据，并将其反序列化为MonitorRequest对象
            MonitorRequest monitorRequest = Json.fromJson(stringRedisTemplate.opsForValue().get(key), MonitorRequest.class);
            // 将心跳数据添加到对应的应用名称的列表中
            rs.computeIfAbsent(monitorRequest.getAppName(), it -> new LinkedList<>()).add(monitorRequest);
        }
        return rs;
    }

    /**
     * 获取心跳数据
     * @return 心跳数据的映射表，以应用名称为键，心跳数据列表为值
     */
    public Map<String, List<MonitorRequest>> getHeart() {
        // 获取所有的心跳数据键
        Set<String> keys = stringRedisTemplate.keys(HEART + "*");
        return create(keys);
    }
}

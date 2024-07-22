package com.chua.starter.monitor.server.factory;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.constant.MonitorConstant;
import com.chua.starter.monitor.server.pojo.IpInstance;
import com.chua.starter.monitor.server.pojo.ServiceTarget;
import com.chua.starter.monitor.server.service.IptablesService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 监控服务器工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Service@SuppressWarnings("ALL")
public class MonitorServerFactory implements MonitorConstant, DisposableBean, InitializingBean {

    @AutoInject
    private RedisTemplate stringRedisTemplate;

    @AutoInject
    private IptablesService iptablesService;

    /**
     * 获取心跳数据
     * @return 心跳数据的映射表，以应用名称为键，心跳数据列表为值
     */
    public List<MonitorRequest> getHeart(String appName) {
        // 获取所有的心跳数据键
        Set<String> keys = stringRedisTemplate.keys(HEART + appName + ":*");
        Map<String, List<MonitorRequest>> stringListMap = create(keys);
        if(stringListMap.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(stringListMap.get(appName));
    }
    /**
     * 获取心跳数据
     * @return 心跳数据的映射表，以应用名称为键，心跳数据列表为值
     */
    public MonitorRequest getHeart(String appName, String appModel) {
        // 获取所有的心跳数据键
         Set<String> keys = stringRedisTemplate.keys(HEART + appName + ":" +
                StringUtils.defaultString(appModel, "").replace(":", "_"));
        Map<String, List<MonitorRequest>> stringListMap = create(keys);
        if(stringListMap.isEmpty()) {
            return null;
        }
        return CollectionUtils.findFirst(stringListMap.get(appName));
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
            MonitorRequest monitorRequest = (MonitorRequest) stringRedisTemplate.opsForValue().get(key);
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

    /**
     * 获取服务实例
     *
     * @param appName       应用程序名称
     * @param serverAddress 服务器地址
     * @return {@link List}<{@link ServiceTarget}>
     */
    public List<ServiceTarget> getServiceInstance(String appName, String serverAddress) {
        List<ServiceTarget> rs = new ArrayList<>();
        Set<String> keys = getKeys(appName, serverAddress, "SERVER", false);
        for (String key : keys) {
            try {
                List<String> strings = stringRedisTemplate.opsForZSet().randomMembers(key, 100);
                if(CollectionUtils.isEmpty(strings)) {
                    continue;
                }
                for (String string : strings) {
                    rs.add(Json.fromJson(string, ServiceTarget.class));
                }
            } catch (Exception ignored) {
            }
        }

        return rs;

    }

    private Set<String> getKeys(String appName, String serverAddress, String type, boolean deep) {
        if(StringUtils.isEmpty(serverAddress)) {
            return (Set<String>) Optional.ofNullable(stringRedisTemplate.keys(REPORT + appName + ":*"))
                    .orElse(Collections.emptySet())
                    .stream().filter(it -> it.toString().endsWith(type))
                    .collect(Collectors.toSet());
        }
        return Optional.ofNullable(stringRedisTemplate.keys(REPORT + appName + ":" + serverAddress+ ":" + type + (deep ? ":*" : "")))
                .orElse(Collections.emptySet());
    }

    /**
     * 列表系统
     *
     * @return {@link List}<{@link ServiceTarget}>
     */
    public List<ServiceTarget> listSys() {
        Set keys = stringRedisTemplate.keys(HEART + "*");

        return Collections.emptyList();
    }

    @Override
    public void destroy() throws Exception {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    public List<IpInstance> getIpInstance(String appName, String serverAddress) {
        List<IpInstance> rs = new ArrayList<>();
        Set<String> keys = getKeys(appName, serverAddress, "IP", true);
        for (String key : keys) {
            try {
                String[] split = key.split(":");
                String k1 = ArrayUtils.last(split);
                ReturnResult<GeoCity> geoCityReturnResult = iptablesService.transferAddress(k1);
                GeoCity data = geoCityReturnResult.getData();
                IpInstance item = new IpInstance();
                item.setIp(k1);
                item.setCity(null != data ? data.getCity() : null);
                item.setCount(NumberUtils.toLong(stringRedisTemplate.opsForValue().get(key) + "", 0L));
                rs.add(item);
            } catch (Exception ignored) {
            }
        }

        return rs;
    }
}

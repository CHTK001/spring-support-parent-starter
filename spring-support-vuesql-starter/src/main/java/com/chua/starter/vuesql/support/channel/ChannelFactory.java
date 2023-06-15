package com.chua.starter.vuesql.support.channel;

import com.chua.common.support.function.SafePredicate;
import com.chua.common.support.utils.DigestUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 管道工厂
 *
 * @author CH
 */
@Component
public class ChannelFactory implements DisposableBean {

    private static final Map<String, AutoCloseable> CONNECTION_FACTORY = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> NAME_MD5 = new ConcurrentHashMap<>();
    /**
     * 断开连接
     *
     * @param websqlConfig 数据库配置
     * @return 获取连接
     */
    public synchronized void disConnection(WebsqlConfig websqlConfig) {
        if(null == websqlConfig) {
            return;
        }
        String key = getKey(websqlConfig);
        AutoCloseable o = CONNECTION_FACTORY.get(key);
        try {
            o.close();
            CONNECTION_FACTORY.remove(key);
            NAME_MD5.get(websqlConfig.getConfigName()).remove(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 更新连接
     *
     * @param websqlConfig 数据库配置
     * @param targetType   类型
     * @return 获取连接
     */
    public synchronized <C>void updateConnection(WebsqlConfig websqlConfig, Class<C> targetType, BiFunction<C, WebsqlConfig, C> function) {
        if(null ==websqlConfig) {
            return;
        }
        String key = getKey(websqlConfig);
        Object o = CONNECTION_FACTORY.get(key);
        CONNECTION_FACTORY.put(key, (AutoCloseable) function.apply((C) o, websqlConfig));
    }

    /**
     * 获取连接
     *
     * @param websqlConfig 数据库配置
     * @param targetType   类型
     * @return 获取连接
     */
    public synchronized <C> C getConnection(WebsqlConfig websqlConfig, Class<C> targetType,
                                            Function<WebsqlConfig, C> function,
                                            SafePredicate<C> predicate
                                            ) {
        if (websqlConfig == null) {
            return null;
        }

        String key = getKey(websqlConfig);
        AutoCloseable o = CONNECTION_FACTORY.get(key);
        if (o != null) {
            if(!predicate.test((C) o)) {
                return (C) o;
            }
            disConnection(websqlConfig);
        }

        C apply = function.apply(websqlConfig);
        CONNECTION_FACTORY.put(key, (AutoCloseable) apply);
        return apply;
    }

    /**
     * key
     *
     * @param config config
     * @return key
     */
    private static String getKey(WebsqlConfig config) {
        String md5Hex = DigestUtils.md5Hex(config.toString());
        check(config.getConfigName(), md5Hex);
        NAME_MD5.computeIfAbsent(config.getConfigName(), it -> new LinkedHashSet<>()).add(md5Hex);
        return md5Hex;
    }

    private static void check(String configName, String md5Hex) {
        Set<String> strings = NAME_MD5.get(configName);
        if(null == strings) {
            return;
        }
        List<String> collect = strings.stream().filter(it -> !md5Hex.equals(it)).collect(Collectors.toList());
        for (String s : collect) {
            try (AutoCloseable autoCloseable = CONNECTION_FACTORY.get(s)) {
                CONNECTION_FACTORY.remove(s);
                strings.remove(s);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        for (AutoCloseable value : CONNECTION_FACTORY.values()) {
            try {
                value.close();
            } catch (Exception ignored) {
            }
        }
    }
}

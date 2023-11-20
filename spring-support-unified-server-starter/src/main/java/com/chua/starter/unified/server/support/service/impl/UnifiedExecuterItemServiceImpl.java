package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterItemMapper;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.google.common.cache.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;
import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;
import static com.chua.starter.common.support.constant.Constant.EXECUTER;

@Service
@Slf4j
public class UnifiedExecuterItemServiceImpl extends ServiceImpl<UnifiedExecuterItemMapper, UnifiedExecuterItem>
        implements UnifiedExecuterItemService, RemovalListener<String, UnifiedExecuterItem>, InitializingBean {


    private Cache<String, UnifiedExecuterItem> cache;
    @Resource
    private UnifiedServerProperties unifiedServerProperties;


    private final BiMap<String, Integer> appNameAndId  = HashBiMap.create();

    @Resource
    private UnifiedExecuterService unifiedExecuterService;

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "'all'")
    public List<UnifiedExecuterItem> getAll() {
        return baseMapper.selectList(new MPJLambdaWrapper<UnifiedExecuterItem>()
                .selectAll(UnifiedExecuterItem.class)
                .select(UnifiedExecuter::getUnifiedExecuterName)
                .innerJoin(UnifiedExecuter.class, UnifiedExecuter::getUnifiedExecuterId, UnifiedExecuterItem::getUnifiedExecuterId)
        );
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "#unifiedConfigProfile")
    public List<UnifiedExecuterItem> findItem(String unifiedConfigProfile) {
        return baseMapper.selectList(Wrappers.<UnifiedExecuterItem>lambdaQuery().eq(UnifiedExecuterItem::getUnifiedExecuterItemProfile, unifiedConfigProfile));
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "#unifiedExecuterId")
    public void remove(Serializable unifiedExecuterId) {
        baseMapper.delete(Wrappers.<UnifiedExecuterItem>lambdaUpdate()
                .eq(UnifiedExecuterItem::getUnifiedExecuterId, unifiedExecuterId));
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, allEntries = true)
    public boolean saveOrUpdate(UnifiedExecuterItem entity) {
        LambdaQueryWrapper<UnifiedExecuterItem> wrapper = Wrappers.<UnifiedExecuterItem>lambdaQuery()
                .eq(UnifiedExecuterItem::getUnifiedExecuterId, entity.getUnifiedExecuterId())
                .eq(UnifiedExecuterItem::getUnifiedExecuterItemHost, entity.getUnifiedExecuterItemHost());
        Long aLong = baseMapper.selectCount(wrapper);
        if(aLong > 0L) {
            baseMapper.update(entity, wrapper);
            return true;
        }
        baseMapper.insert(entity);
        return true;
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, allEntries = true)
    public void saveOrUpdate(UnifiedExecuterItem unifiedExecuterItem, UnifiedExecuter unifiedExecuter) {
        String appname = unifiedExecuter.getUnifiedAppname();
        if(StringUtils.isNotBlank(appname)) {
            appNameAndId.put(appname, unifiedExecuter.getUnifiedExecuterId());
        }
        saveOrUpdate(unifiedExecuterItem, Wrappers.<UnifiedExecuterItem>lambdaUpdate()
                .eq(UnifiedExecuterItem::getUnifiedExecuterId, unifiedExecuter.getUnifiedExecuterId())
                .eq(UnifiedExecuterItem::getUnifiedExecuterItemHost, unifiedExecuterItem.getUnifiedExecuterItemHost()));
    }

    @Override
    public void checkHeart(BootRequest request) {
        boolean haveItem = checkHaveItem(request);
        if(haveItem) {
            return;
        }
        register(request);
    }

    private void register(BootRequest request) {
        String appName = request.getAppName();
        if(null == appName || !appNameAndId.containsKey(appName)) {
            return;
        }
        UnifiedExecuterItem item = new UnifiedExecuterItem();
        JSONObject jsonObject = JSON.parseObject(request.getContent());
        item.setUnifiedExecuterItemPort(jsonObject.getString("port"));
        item.setUnifiedExecuterItemHost(jsonObject.getString("host"));
        item.setUnifiedExecuterItemSubscribe(jsonObject.getString(SUBSCRIBE));
        item.setUnifiedExecuterItemProfile(request.getProfile());
        item.setUnifiedExecuterItemProtocol(request.getProtocol());
        item.setCreateTime(new Date());
        item.setUnifiedExecuterId(appNameAndId.get(appName));
        saveOrUpdate(item);
    }

    private boolean checkHaveItem(BootRequest request) {
        String content = request.getContent();
        String appName = request.getAppName();
        JSONObject jsonObject = JSON.parseObject(content);
        String host = jsonObject.getString("host");
        String key = appName + "" + host;
        UnifiedExecuterItem ifPresent = cache.getIfPresent(key);
        if(null == ifPresent) {
            return false;
        }
        cache.put(key, ifPresent);
        return true;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        if(null != cache) {
            return;
        }
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(unifiedServerProperties.getKeepAliveTimeout(), TimeUnit.SECONDS)
                .removalListener(this)
                .build();

        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<UnifiedExecuter> list = unifiedExecuterService.list();
                for (UnifiedExecuter unifiedExecuter : list) {
                    appNameAndId.put(unifiedExecuter.getUnifiedExecuterName(), unifiedExecuter.getUnifiedExecuterId());
                }

                List<UnifiedExecuterItem> list1 = list();
                BiMap<Integer, String> inverse = appNameAndId.inverse();
                for (UnifiedExecuterItem unifiedExecuter : list1) {
                    String key = inverse.get(unifiedExecuter.getUnifiedExecuterId()) + "" + unifiedExecuter.getUnifiedExecuterItemHost();
                    cache.put(key, unifiedExecuter);
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onRemoval(RemovalNotification<String, UnifiedExecuterItem> notification) {
        UnifiedExecuterItem value = notification.getValue();
        if(null == value) {
            return;
        }

        if(notification.getCause() != RemovalCause.EXPIRED) {
            return;
        }
        log.warn("{}:{}({})心跳过期", value.getUnifiedExecuterItemHost(), value.getUnifiedExecuterItemPort(), value.getUnifiedExecuterItemId());
        try {
            remove(value.getUnifiedExecuterId());
        } catch (Exception ignored) {
        }
    }
}

package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.common.support.value.TimeValue;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterItemMapper;
import com.chua.starter.unified.server.support.pojo.ActuatorQuery;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;
import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;
import static com.chua.starter.common.support.constant.Constant.EXECUTER;

@Service
@Slf4j
public class UnifiedExecuterItemServiceImpl extends NotifyServiceImpl<UnifiedExecuterItemMapper, UnifiedExecuterItem>
        implements UnifiedExecuterItemService, InitializingBean {


    private Map<String, TimeValue<UnifiedExecuterItem>> cache = new ConcurrentHashMap<>();
    @Resource
    private UnifiedServerProperties unifiedServerProperties;


    private final BiMap<String, Integer> appNameAndId  = HashBiMap.create();

    @Resource
    private UnifiedExecuterService unifiedExecuterService;

    private ScheduledExecutorService scheduledExecutorService;

    public UnifiedExecuterItemServiceImpl() {
        setGetUnifiedId(UnifiedExecuterItem::getUnifiedExecuterId);
        setGetProfile(UnifiedExecuterItem::getUnifiedExecuterItemProfile);
        setGetAppName(UnifiedExecuterItem::getUnifiedAppname);
        setModuleType(ModuleType.OSHI);
    }

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
    public JSONObject getOshi(String dataId) {
        UnifiedExecuterItem unifiedExecuterItem = ((UnifiedExecuterItemService) AopContext.currentProxy()).get(dataId);
        if(null == unifiedExecuterItem) {
            return new JSONObject();
        }

        JSONObject rs = new JSONObject();
        setInLog(false);
        setModuleType(ModuleType.OSHI);
        setResponseConsumer(response -> rs.putAll(JSON.parseObject(response.getContent())));
        setRequestConsumer(request -> {
            request.setContent(null);
        });
        notifyClient(unifiedExecuterItem, unifiedExecuterItem);
        return rs;
    }


    @Override
    public Page<JSONObject> getProcess(String dataId, String status, String keyword, Integer page, Integer pageSize) {
        UnifiedExecuterItem unifiedExecuterItem = ((UnifiedExecuterItemService) AopContext.currentProxy()).get(dataId);
        if(null == unifiedExecuterItem) {
            return new Page<>();
        }

        JSONObject rs = new JSONObject();
        setInLog(false);
        setModuleType(ModuleType.PROCESS);
        setResponseConsumer(response -> rs.putAll(JSON.parseObject(response.getContent())));
        setRequestConsumer(request -> {
            request.addParam("status", status);
            request.addParam("keyword", keyword);
            request.addParam("page", page);
            request.addParam("pageSize", pageSize);
            request.setContent(null);
        });
        notifyClient(unifiedExecuterItem, unifiedExecuterItem);
        return rs.getObject("process", Page.class);
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "#unifiedConfigProfile")
    public List<UnifiedExecuterItem> findItem(String unifiedConfigProfile) {
        return baseMapper.selectList(Wrappers.<UnifiedExecuterItem>lambdaQuery().eq(UnifiedExecuterItem::getUnifiedExecuterItemProfile, unifiedConfigProfile));
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "#unifiedExecuterItemId")
    public void remove(Serializable unifiedExecuterItemId) {
        UnifiedExecuterItem executerItem = get(unifiedExecuterItemId);
        String key = executerItem.getUnifiedAppname() + "" + executerItem.getUnifiedExecuterItemHost() + executerItem.getUnifiedExecuterItemPort();
        cache.remove(key);
        baseMapper.deleteById(unifiedExecuterItemId);
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, allEntries = true)
    public void removeExecuterId(Serializable unifiedExecuterId) {
        LambdaQueryWrapper<UnifiedExecuterItem> wrapper = Wrappers.<UnifiedExecuterItem>lambdaQuery().eq(UnifiedExecuterItem::getUnifiedExecuterId, unifiedExecuterId);
        List<UnifiedExecuterItem> list = list(wrapper);
        for (UnifiedExecuterItem unifiedExecuterItem : list) {
            String key = unifiedExecuterItem.getUnifiedAppname() + "" + unifiedExecuterItem.getUnifiedExecuterItemHost() + unifiedExecuterItem.getUnifiedExecuterItemPort();
            cache.remove(key);
        }
        baseMapper.delete(wrapper);
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, key = "#unifiedExecuterItemId")
    public UnifiedExecuterItem get(Serializable unifiedExecuterItemId) {
        return baseMapper.selectOne(new MPJLambdaWrapper<UnifiedExecuterItem>()
                .selectAll(UnifiedExecuterItem.class)
                .selectAs(UnifiedExecuter::getUnifiedAppname, UnifiedExecuterItem::getUnifiedAppname)
                .innerJoin(UnifiedExecuter.class, UnifiedExecuter::getUnifiedExecuterId, UnifiedExecuterItem::getUnifiedExecuterId)
                .eq(UnifiedExecuterItem::getUnifiedExecuterItemId, unifiedExecuterItemId)
        );
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = EXECUTER, allEntries = true)
    public boolean saveOrUpdate(UnifiedExecuterItem entity) {
        LambdaQueryWrapper<UnifiedExecuterItem> wrapper = Wrappers.<UnifiedExecuterItem>lambdaQuery()
                .eq(UnifiedExecuterItem::getUnifiedExecuterId, entity.getUnifiedExecuterId())
                .eq(UnifiedExecuterItem::getUnifiedExecuterItemHost, entity.getUnifiedExecuterItemHost())
                .eq(UnifiedExecuterItem::getUnifiedExecuterItemPort, entity.getUnifiedExecuterItemPort()
                );
        UnifiedExecuterItem unifiedExecuterItem = baseMapper.selectOne(wrapper);
        if(unifiedExecuterItem != null) {
            entity.setUnifiedExecuterId(unifiedExecuterItem.getUnifiedExecuterId());
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

    @Override
    public ActuatorQuery getActuatorQuery(String dataId) {
        UnifiedExecuterItem unifiedExecuterItem = get(dataId);
        if (null == unifiedExecuterItem) {
            return null;
        }
        String unifiedExecuterItemSubscribe = unifiedExecuterItem.getUnifiedExecuterItemSubscribe();
        if(StringUtils.isEmpty(unifiedExecuterItemSubscribe)) {
            return null;
        }

        com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSON.parseObject(unifiedExecuterItemSubscribe);
        UnifiedClientProperties.SubscribeOption subscribeOption = jsonObject.getObject(ModuleType.ACTUATOR.name(), UnifiedClientProperties.SubscribeOption.class);
        if(null == subscribeOption) {
            return null;
        }

        Map<String, Object> ext = subscribeOption.getExt();
        if(null == ext) {
            return null;
        }


        String port = MapUtils.getString(ext, "port");
        if(StringUtils.isEmpty(port)) {
            return null;
        }
        return new ActuatorQuery(unifiedExecuterItem.getUnifiedExecuterItemHost(), port,
                MapUtils.getString(ext, "contextPath", ""),
                MapUtils.getString(ext, "endpointsUrl", "/actuator"));
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
        String key = appName + "" + item.getUnifiedExecuterItemHost() + item.getUnifiedExecuterItemPort();
        cache.put(key, TimeValue.of(item, unifiedServerProperties.getKeepAliveTimeout()));
    }

    private boolean checkHaveItem(BootRequest request) {
        String content = request.getContent();
        String appName = request.getAppName();
        JSONObject jsonObject = JSON.parseObject(content);
        String host = jsonObject.getString("host");
        String port = jsonObject.getString("port");
        String key = appName + "" + host + port;
        TimeValue<UnifiedExecuterItem> timeValue = cache.get(key);
        if(null == timeValue) {
            return false;
        }
        timeValue.refresh();
        return true;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, TimeValue<UnifiedExecuterItem>> entry : cache.entrySet()) {
                TimeValue<UnifiedExecuterItem> entryValue = entry.getValue();
                if(entryValue.isTimeout()) {
                    UnifiedExecuterItem executerItem = entryValue.getOrigin();
                    if(null == executerItem) {
                        continue;
                    }
                    log.warn("{}:{}({})心跳过期", executerItem.getUnifiedExecuterItemHost(), executerItem.getUnifiedExecuterItemPort(), executerItem.getUnifiedExecuterItemId());
                    try {
                        remove(executerItem.getUnifiedExecuterItemId());
                        cache.remove(entry.getKey());
                    } catch (Exception ignored) {
                    }
                }
            }
        }, 0, unifiedServerProperties.getKeepAliveTimeout() + 10, TimeUnit.SECONDS);
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<UnifiedExecuter> list = unifiedExecuterService.list();
                for (UnifiedExecuter unifiedExecuter : list) {
                    appNameAndId.put(unifiedExecuter.getUnifiedExecuterName(), unifiedExecuter.getUnifiedExecuterId());
                }

                remove(new MPJLambdaWrapper<UnifiedExecuterItem>()
                        .selectAll(UnifiedExecuterItem.class)
                        .innerJoin(UnifiedExecuter.class, UnifiedExecuter::getUnifiedExecuterId, UnifiedExecuterItem::getUnifiedExecuterId)
                        .eq(UnifiedExecuter::getUnifiedExecuterType, 0)
                );
            } catch (Exception ignored) {
            }
        });
    }
}

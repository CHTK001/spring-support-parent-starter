package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterMapper;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.chua.common.support.constant.CommonConstant.ONE_STR;
import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 统一执行器服务impl
 *
 * @author CH
 */
@Service
public class UnifiedExecuterServiceImpl extends ServiceImpl<UnifiedExecuterMapper, UnifiedExecuter> implements UnifiedExecuterService{


    private static final String STR_1 = "1";
    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;
    @Resource
    private UnifiedServerProperties unifiedServerProperties;

    @Override
    public void createExecutor(BootRequest request) {
        UnifiedExecuter unifiedExecuter = baseMapper.selectOne(Wrappers.<UnifiedExecuter>lambdaQuery().eq(UnifiedExecuter::getUnifiedExecuterName, request.getAppName()));
        if(null == unifiedExecuter) {
            unifiedExecuter = new UnifiedExecuter();
            unifiedExecuter.setCreateTime(new Date());
            unifiedExecuter.setUnifiedExecuterType("0");

        }

        if(STR_1.equals(unifiedExecuter.getUnifiedExecuterType())) {
            return;
        }

        registerExecutor(unifiedExecuter, request);
        registerExecutorItem(unifiedExecuter, request);
    }

    @Override
    public Boolean saveOrUpdateExecuter(UnifiedExecuter t) {
        return transactionTemplate.execute(status -> {
            if(null != t.getUnifiedExecuterId()) {
                baseMapper.updateById(t);
                checkItem(t);
                return true;
            }
            baseMapper.insert(t);
            checkItem(t);
            return true;
        });
    }

    @Override
    public Boolean updateByIdExecuter(UnifiedExecuter t) {
        return transactionTemplate.execute(status -> {
            if(ONE_STR.equalsIgnoreCase(t.getUnifiedExecuterType())) {
                unifiedExecuterItemService.removeExecuterId(t.getUnifiedExecuterId());
            }
            baseMapper.updateById(t);
            return true;
        });
    }

    @Override
    public Boolean removeByIdExecuter(String id) {
        return transactionTemplate.execute(status -> {
            unifiedExecuterItemService.removeExecuterId(id);
            baseMapper.deleteById(id);
            return true;
        });
    }

    @Override
    public IPage<UnifiedExecuter> pageExecuter(DelegatePage<UnifiedExecuter> page, UnifiedExecuter entity) {
        Page<UnifiedExecuter> unifiedExecuterPage = baseMapper.selectPage(page.createPage(), Wrappers.<UnifiedExecuter>lambdaQuery()
                .like(StringUtils.isNotBlank(entity.getUnifiedAppname()), UnifiedExecuter::getUnifiedAppname, entity.getUnifiedAppname())
        );

        UnifiedServerProperties.EndpointOption endpoint = unifiedServerProperties.getEndpoint();
        String url = endpoint.getHost();
        Integer port = endpoint.getPort();


        List<UnifiedExecuter> records = unifiedExecuterPage.getRecords();
        if(!records.isEmpty()) {
            List<UnifiedExecuterItem> list = unifiedExecuterItemService
                    .list(Wrappers.<UnifiedExecuterItem>lambdaQuery()
                            .in(UnifiedExecuterItem::getUnifiedExecuterId, records.stream().map(UnifiedExecuter::getUnifiedExecuterId).collect(Collectors.toSet())));
            Map<Integer, List<UnifiedExecuterItem>> tpl = new HashMap<>(list.size());
            for (UnifiedExecuterItem item : list) {
                tpl.computeIfAbsent(item.getUnifiedExecuterId(), it -> new LinkedList<>()).add(item);
            }

            for (UnifiedExecuter record : records) {
                record.setItem(tpl.get(record.getUnifiedExecuterId()));
                record.setOpenLogBtn(StringUtils.isNotBlank(url) && null != port);
            }
        }


        return unifiedExecuterPage;
    }

    @Override
    public Integer getIdByName(String appName) {
        UnifiedExecuter unifiedExecuter = getOne(Wrappers.<UnifiedExecuter>lambdaQuery().eq(UnifiedExecuter::getUnifiedAppname, appName));
        return null == unifiedExecuter ? null : unifiedExecuter.getUnifiedExecuterId();
    }

    private void checkItem(UnifiedExecuter t) {
        List<UnifiedExecuterItem> item = t.getItem();
        unifiedExecuterItemService.remove(Wrappers.<UnifiedExecuterItem>lambdaUpdate().eq(UnifiedExecuterItem::getUnifiedExecuterId, t.getUnifiedExecuterId()));
        if(ONE_STR.equalsIgnoreCase(t.getUnifiedExecuterType()) && CollectionUtils.isNotEmpty(item)) {
            for (UnifiedExecuterItem unifiedExecuterItem : item) {
                unifiedExecuterItem.setUnifiedExecuterId(t.getUnifiedExecuterId());
            }
            unifiedExecuterItemService.saveBatch(item);
        }
    }
    /**
     * 注册执行器子项
     *
     * @param unifiedExecuter 统一执行器
     * @param request         请求
     */
    private void registerExecutorItem(UnifiedExecuter unifiedExecuter, BootRequest request) {
        UnifiedExecuterItem unifiedExecuterItem = new UnifiedExecuterItem();
        JSONObject jsonObject = JSONObject.parseObject(request.getContent());
        unifiedExecuterItem.setUnifiedExecuterId(unifiedExecuter.getUnifiedExecuterId());
        unifiedExecuterItem.setUnifiedExecuterItemHost(jsonObject.getString("host"));
        unifiedExecuterItem.setUnifiedExecuterItemPort(jsonObject.getString("port"));
        unifiedExecuterItem.setUnifiedExecuterItemProfile(request.getProfile());
        unifiedExecuterItem.setUnifiedExecuterItemProtocol(request.getProtocol());
        unifiedExecuterItem.setUnifiedExecuterItemSubscribe(jsonObject.getString(SUBSCRIBE));
        unifiedExecuterItem.setUpdateTime(new Date());

        unifiedExecuterItemService.saveOrUpdate(unifiedExecuterItem, unifiedExecuter);
    }

    /**
     * 注册执行器
     *
     * @param unifiedExecuter 统一执行人
     * @param request         请求
     */
    private void registerExecutor(UnifiedExecuter unifiedExecuter, BootRequest request) {
        unifiedExecuter.setUnifiedExecuterName(request.getAppName());
        unifiedExecuter.setUnifiedAppname(request.getAppName());
        unifiedExecuter.setUpdateTime(new Date());
        Integer unifiedExecuterId = unifiedExecuter.getUnifiedExecuterId();
        if(null == unifiedExecuterId) {
            baseMapper.insert(unifiedExecuter);
            return;
        }
        baseMapper.updateById(unifiedExecuter);
    }
}

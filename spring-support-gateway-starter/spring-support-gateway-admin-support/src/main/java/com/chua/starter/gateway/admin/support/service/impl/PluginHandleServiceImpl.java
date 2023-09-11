/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chua.starter.gateway.admin.support.service.impl;

import org.apache.commons.collections4.CollectionUtils;
import com.chua.starter.gateway.admin.support.aspect.annotation.Pageable;
import com.chua.starter.gateway.admin.support.mapper.PluginHandleMapper;
import com.chua.starter.gateway.admin.support.mapper.ShenyuDictMapper;
import com.chua.starter.gateway.admin.support.model.dto.PluginHandleDTO;
import com.chua.starter.gateway.admin.support.model.entity.BaseDO;
import com.chua.starter.gateway.admin.support.model.entity.PluginHandleDO;
import com.chua.starter.gateway.admin.support.model.event.plugin.BatchPluginDeletedEvent;
import com.chua.starter.gateway.admin.support.model.page.CommonPager;
import com.chua.starter.gateway.admin.support.model.page.PageResultUtils;
import com.chua.starter.gateway.admin.support.model.query.PluginHandleQuery;
import com.chua.starter.gateway.admin.support.model.vo.PluginHandleVO;
import com.chua.starter.gateway.admin.support.model.vo.ShenyuDictVO;
import com.chua.starter.gateway.admin.support.service.PluginHandleService;
import com.chua.starter.gateway.admin.support.service.publish.PluginHandleEventPublisher;
import org.apache.shenyu.common.utils.ListUtil;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link PluginHandleService}.
 */
@Service
public class PluginHandleServiceImpl implements PluginHandleService {
    
    private static final int SELECT_BOX_DATA_TYPE = 3;
    
    private final PluginHandleMapper pluginHandleMapper;
    
    private final ShenyuDictMapper shenyuDictMapper;
    
    private final PluginHandleEventPublisher eventPublisher;
    
    public PluginHandleServiceImpl(final PluginHandleMapper pluginHandleMapper,
                                   final ShenyuDictMapper shenyuDictMapper,
                                   final PluginHandleEventPublisher eventPublisher) {
        this.pluginHandleMapper = pluginHandleMapper;
        this.shenyuDictMapper = shenyuDictMapper;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    @Pageable
    public CommonPager<PluginHandleVO> listByPage(final PluginHandleQuery pluginHandleQuery) {
        List<PluginHandleDO> pluginHandleDOList = pluginHandleMapper.selectByQuery(pluginHandleQuery);
        return PageResultUtils.result(pluginHandleQuery.getPageParameter(), () -> this.buildPluginHandleVO(pluginHandleDOList));
    }
    
    @Override
    public Integer create(final PluginHandleDTO pluginHandleDTO) {
        PluginHandleDO pluginHandleDO = PluginHandleDO.buildPluginHandleDO(pluginHandleDTO);
        int pluginHandleCount = pluginHandleMapper.insertSelective(pluginHandleDO);
        if (pluginHandleCount > 0) {
            eventPublisher.onCreated(pluginHandleDO);
        }
        return pluginHandleCount;
    }
    
    @Override
    public Integer update(final PluginHandleDTO pluginHandleDTO) {
        PluginHandleDO pluginHandleDO = PluginHandleDO.buildPluginHandleDO(pluginHandleDTO);
        final PluginHandleDO before = pluginHandleMapper.selectById(pluginHandleDTO.getId());
        int pluginHandleCount = pluginHandleMapper.updateByPrimaryKeySelective(pluginHandleDO);
        if (pluginHandleCount > 0) {
            eventPublisher.onUpdated(pluginHandleDO, before);
        }
        return pluginHandleCount;
    }
    
    @Override
    public Integer deletePluginHandles(final List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        final List<PluginHandleDO> handles = pluginHandleMapper.selectByIdList(ids);
        final int count = pluginHandleMapper.deleteByIdList(ids);
        if (count > 0) {
            eventPublisher.onDeleted(handles);
        }
        return count;
    }
    
    @Override
    public PluginHandleVO findById(final String id) {
        return buildPluginHandleVO(pluginHandleMapper.selectById(id));
    }
    
    @Override
    public List<PluginHandleVO> list(final String pluginId, final Integer type) {
        List<PluginHandleDO> pluginHandleDOList = pluginHandleMapper.selectByQuery(PluginHandleQuery.builder()
                .pluginId(pluginId)
                .type(type)
                .build());
        return buildPluginHandleVO(pluginHandleDOList);
    }
    
    /**
     * The associated Handle needs to be deleted synchronously.
     *
     * @param event event
     */
    @EventListener(value = BatchPluginDeletedEvent.class)
    public void onPluginDeleted(final BatchPluginDeletedEvent event) {
        deletePluginHandles(ListUtil.map(pluginHandleMapper.selectByPluginIdList(event.getDeletedPluginIds()), BaseDO::getId));
    }
    
    private PluginHandleVO buildPluginHandleVO(final PluginHandleDO pluginHandleDO) {
        List<ShenyuDictVO> dictOptions = null;
        if (pluginHandleDO.getDataType() == SELECT_BOX_DATA_TYPE) {
            dictOptions = ListUtil.map(shenyuDictMapper.findByType(pluginHandleDO.getField()), ShenyuDictVO::buildShenyuDictVO);
        }
        return PluginHandleVO.buildPluginHandleVO(pluginHandleDO, dictOptions);
    }
    
    private List<PluginHandleVO> buildPluginHandleVO(final List<PluginHandleDO> pluginHandleDOList) {
        
        List<String> fieldList = pluginHandleDOList.stream()
                .filter(pluginHandleDO -> pluginHandleDO.getDataType() == SELECT_BOX_DATA_TYPE)
                .map(PluginHandleDO::getField)
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, List<ShenyuDictVO>> shenyuDictMap = CollectionUtils.isNotEmpty(fieldList)
                ? Optional.ofNullable(shenyuDictMapper.findByTypeBatch(fieldList))
                .orElseGet(ArrayList::new)
                .stream()
                .map(ShenyuDictVO::buildShenyuDictVO)
                .collect(Collectors.groupingBy(ShenyuDictVO::getType))
                : new HashMap<>(0);
        
        return pluginHandleDOList.stream()
                .map(pluginHandleDO -> {
                    List<ShenyuDictVO> dictOptions = shenyuDictMap.get(pluginHandleDO.getField());
                    return PluginHandleVO.buildPluginHandleVO(pluginHandleDO, dictOptions);
                })
                .collect(Collectors.toList());
    }
}

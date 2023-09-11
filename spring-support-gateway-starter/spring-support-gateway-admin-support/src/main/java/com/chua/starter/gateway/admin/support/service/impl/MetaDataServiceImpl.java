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

import java.util.LinkedList;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.chua.starter.gateway.admin.support.aspect.annotation.Pageable;
import com.chua.starter.gateway.admin.support.listener.DataChangedEvent;
import com.chua.starter.gateway.admin.support.mapper.MetaDataMapper;
import com.chua.starter.gateway.admin.support.model.dto.MetaDataDTO;
import com.chua.starter.gateway.admin.support.model.entity.MetaDataDO;
import com.chua.starter.gateway.admin.support.model.page.CommonPager;
import com.chua.starter.gateway.admin.support.model.page.PageResultUtils;
import com.chua.starter.gateway.admin.support.model.query.MetaDataQuery;
import com.chua.starter.gateway.admin.support.model.vo.MetaDataVO;
import com.chua.starter.gateway.admin.support.service.MetaDataService;
import com.chua.starter.gateway.admin.support.service.publish.MetaDataEventPublisher;
import com.chua.starter.gateway.admin.support.transfer.MetaDataTransfer;
import com.chua.starter.gateway.admin.support.utils.Assert;
import org.apache.shenyu.common.utils.ListUtil;
import com.chua.starter.gateway.admin.support.utils.ShenyuResultMessage;
import org.apache.shenyu.common.constant.AdminConstants;
import org.apache.shenyu.common.dto.MetaData;
import org.apache.shenyu.common.enums.ConfigGroupEnum;
import org.apache.shenyu.common.enums.DataEventTypeEnum;
import org.apache.shenyu.common.utils.UUIDUtils;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link MetaDataService}.
 */
@Service
public class MetaDataServiceImpl implements MetaDataService {
    
    private final MetaDataMapper metaDataMapper;
    
    private final ApplicationEventPublisher eventPublisher;
    
    private final MetaDataEventPublisher publisher;
    
    public MetaDataServiceImpl(final MetaDataMapper metaDataMapper,
                               final ApplicationEventPublisher eventPublisher,
                               final MetaDataEventPublisher publisher) {
        this.metaDataMapper = metaDataMapper;
        this.eventPublisher = eventPublisher;
        this.publisher = publisher;
    }
    
    @Override
    public void saveOrUpdateMetaData(final MetaDataDO exist, final MetaDataRegisterDTO metaDataDTO) {
        DataEventTypeEnum eventType;
        MetaDataDO metaDataDO = MetaDataTransfer.INSTANCE.mapRegisterDTOToEntity(metaDataDTO);
        if (Objects.isNull(exist)) {
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            metaDataDO.setId(UUIDUtils.getInstance().generateShortUuid());
            metaDataDO.setDateCreated(currentTime);
            metaDataDO.setDateUpdated(currentTime);
            metaDataMapper.insert(metaDataDO);
            eventType = DataEventTypeEnum.CREATE;
        } else {
            metaDataDO.setId(exist.getId());
            metaDataMapper.update(metaDataDO);
            eventType = DataEventTypeEnum.UPDATE;
        }
        // publish MetaData's event
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, eventType,
                Collections.singletonList(MetaDataTransfer.INSTANCE.mapToData(metaDataDO))));
    }
    
    @Override
    public String createOrUpdate(final MetaDataDTO metaDataDTO) {
        return StringUtils.isBlank(metaDataDTO.getId()) ? this.create(metaDataDTO) : this.update(metaDataDTO);
    }
    
    @Override
    public int delete(final List<String> ids) {
        List<MetaDataDO> deletedMetaData = metaDataMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(deletedMetaData)) {
            return 0;
        }
        int count = metaDataMapper.deleteByIdList(ids);
        if (count > 0) {
            publisher.onDeleted(deletedMetaData);
        }
        return count;
    }
    
    @Override
    public String enabled(final List<String> ids, final Boolean enabled) {
        List<MetaDataDO> metaDataDoList = metaDataMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(metaDataDoList)) {
            return AdminConstants.ID_NOT_EXIST;
        }
        for (MetaDataDO metaDataDO : metaDataDoList) {
            metaDataDO.setEnabled(enabled);
        }
        if (metaDataMapper.updateEnableBatch(ids, enabled) > 0) {
            publisher.onEnabled(metaDataDoList);
        }
        return StringUtils.EMPTY;
    }
    
    @Override
    public void syncData() {
        List<MetaDataDO> all = metaDataMapper.findAll();
        if (CollectionUtils.isNotEmpty(all)) {
            eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.REFRESH, MetaDataTransfer.INSTANCE.mapToDataAll(all)));
        }
    }
    
    @Override
    public MetaDataVO findById(final String id) {
        return Optional.ofNullable(MetaDataTransfer.INSTANCE.mapToVO(metaDataMapper.selectById(id))).orElseGet(MetaDataVO::new);
    }
    
    @Override
    @Pageable
    public CommonPager<MetaDataVO> listByPage(final MetaDataQuery metaDataQuery) {
        return PageResultUtils.result(metaDataQuery.getPageParameter(), () -> metaDataMapper.selectByQuery(metaDataQuery)
                .stream()
                .map(MetaDataTransfer.INSTANCE::mapToVO)
                .collect(Collectors.toList()));
    }
    
    @Override
    public List<MetaDataVO> findAll() {
        return MetaDataTransfer.INSTANCE.mapToVOList(metaDataMapper.selectAll());
    }
    
    @Override
    public Map<String, List<MetaDataVO>> findAllGroup() {
        return ListUtil.groupBy(findAll(), MetaDataVO::getAppName);
    }
    
    @Override
    public List<MetaData> listAll() {
        return ListUtil.map(metaDataMapper.selectAll(), MetaDataTransfer.INSTANCE::mapToData);
    }
    
    @Override
    public MetaDataDO findByPath(final String path) {
        return metaDataMapper.findByPath(path);
    }
    
    @Override
    public MetaDataDO findByServiceNameAndMethodName(final String serviceName, final String methodName) {
        final List<MetaDataDO> metadataList = metaDataMapper.findByServiceNameAndMethod(serviceName, methodName);
        return CollectionUtils.isEmpty(metadataList) ? null : metadataList.get(0);
    }
    
    @Override
    public int insert(final MetaDataDO metaDataDO) {
        return metaDataMapper.insert(metaDataDO);
    }
    
    private String create(final MetaDataDTO metaDataDTO) {
        Assert.isNull(metaDataMapper.pathExisted(metaDataDTO.getPath()), AdminConstants.DATA_PATH_IS_EXIST);
        MetaDataDO metaDataDO = MetaDataTransfer.INSTANCE.mapToEntity(metaDataDTO);
        metaDataDO.setId(UUIDUtils.getInstance().generateShortUuid());
        metaDataDO.setPathDesc(Objects.isNull(metaDataDO.getPathDesc()) ? "" : metaDataDO.getPathDesc());
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        metaDataDO.setDateCreated(currentTime);
        metaDataDO.setDateUpdated(currentTime);
        if (metaDataMapper.insert(metaDataDO) > 0) {
            publisher.onCreated(metaDataDO);
        }
        
        // publish MetaData's create event
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.CREATE,
                Collections.singletonList(MetaDataTransfer.INSTANCE.mapToData(metaDataDO))));
        return ShenyuResultMessage.CREATE_SUCCESS;
    }
    
    private String update(final MetaDataDTO metaDataDTO) {
        Assert.isNull(metaDataMapper.pathExistedExclude(metaDataDTO.getPath(), Collections.singletonList(metaDataDTO.getId())), AdminConstants.DATA_PATH_IS_EXIST);
        MetaDataDO metaDataDO = MetaDataTransfer.INSTANCE.mapToEntity(metaDataDTO);
        Optional.ofNullable(metaDataMapper.selectById(metaDataDTO.getId()))
                .ifPresent(e -> metaDataDTO.setEnabled(e.getEnabled()));
        metaDataDO.setPathDesc(Optional.ofNullable(metaDataDO.getPathDesc()).orElse(""));
        final MetaDataDO before = metaDataMapper.selectById(metaDataDO.getId());
        if (metaDataMapper.update(metaDataDO) > 0) {
            publisher.onUpdated(metaDataDO, before);
            // update other rpc_ext for the same service
            final List<MetaDataDO> befores = Optional.ofNullable(metaDataMapper.findByServiceNameAndMethod(
                    metaDataDO.getServiceName(), null)).orElseGet(LinkedList::new);
            for (MetaDataDO b : befores) {
                MetaDataDO update = new MetaDataDO();
                BeanUtils.copyProperties(b, update);
                update.setRpcExt(metaDataDTO.getRpcExt());
                if (metaDataMapper.update(update) > 0) {
                    publisher.onUpdated(update, b);
                }
            }
        }
        
        // publish AppAuthData's update event
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.UPDATE,
                Collections.singletonList(MetaDataTransfer.INSTANCE.mapToData(metaDataDTO))));
        return ShenyuResultMessage.UPDATE_SUCCESS;
    }
    
}
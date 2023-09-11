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

package com.chua.starter.gateway.admin.support.service.publish;

import com.chua.starter.gateway.admin.support.listener.DataChangedEvent;
import com.chua.starter.gateway.admin.support.model.entity.MetaDataDO;
import com.chua.starter.gateway.admin.support.model.enums.EventTypeEnum;
import com.chua.starter.gateway.admin.support.model.event.AdminDataModelChangedEvent;
import com.chua.starter.gateway.admin.support.model.event.metadata.BatchMetaDataChangedEvent;
import com.chua.starter.gateway.admin.support.model.event.metadata.BatchMetaDataDeletedEvent;
import com.chua.starter.gateway.admin.support.model.event.metadata.MetaDataCreatedEvent;
import com.chua.starter.gateway.admin.support.model.event.metadata.MetadataUpdatedEvent;
import com.chua.starter.gateway.admin.support.transfer.MetaDataTransfer;
import org.apache.shenyu.common.utils.ListUtil;
import com.chua.starter.gateway.admin.support.utils.SessionUtil;
import org.apache.shenyu.common.enums.ConfigGroupEnum;
import org.apache.shenyu.common.enums.DataEventTypeEnum;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * MetaDataEventPublisher.
 */
@Component
public class MetaDataEventPublisher implements AdminDataModelChangedEventPublisher<MetaDataDO> {
    
    private final ApplicationEventPublisher publisher;
    
    public MetaDataEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    
    /**
     * on selector created.
     *
     * @param selector selector
     */
    @Override
    public void onCreated(final MetaDataDO selector) {
        publish(new MetaDataCreatedEvent(selector, SessionUtil.visitorName()));
    }
    
    /**
     * on selector updated.
     *
     * @param selector selector
     * @param before   before selector
     */
    @Override
    public void onUpdated(final MetaDataDO selector, final MetaDataDO before) {
        publish(new MetadataUpdatedEvent(selector, before, SessionUtil.visitorName()));
    }
    
    /**
     * on data deleted.
     *
     * @param data data
     */
    @Override
    public void onDeleted(final Collection<MetaDataDO> data) {
        publish(new BatchMetaDataDeletedEvent(data, SessionUtil.visitorName()));
        publisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.DELETE, ListUtil.map(data, MetaDataTransfer.INSTANCE::mapToData)));
    }
    
    /**
     * on metaData batch enabled.
     *
     * @param metaData metaData
     */
    public void onEnabled(final Collection<MetaDataDO> metaData) {
        publish(new BatchMetaDataChangedEvent(metaData, null, EventTypeEnum.META_DATA_UPDATE, SessionUtil.visitorName()));
        publisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.UPDATE, ListUtil.map(metaData, MetaDataTransfer.INSTANCE::mapToData)));
    }
    
    /**
     * event.
     *
     * @param event event.
     */
    @Override
    public void publish(final AdminDataModelChangedEvent event) {
        publisher.publishEvent(event);
    }
}
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

import com.chua.starter.gateway.admin.support.model.entity.DashboardUserDO;
import com.chua.starter.gateway.admin.support.model.event.AdminDataModelChangedEvent;
import com.chua.starter.gateway.admin.support.model.event.user.BatchUserDeletedEvent;
import com.chua.starter.gateway.admin.support.model.event.user.UserCreatedEvent;
import com.chua.starter.gateway.admin.support.model.event.user.UserUpdatedEvent;
import com.chua.starter.gateway.admin.support.utils.SessionUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * UserEventPublisher.
 */
@Component
public class UserEventPublisher implements AdminDataModelChangedEventPublisher<DashboardUserDO> {
    
    private final ApplicationEventPublisher publisher;
    
    public UserEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    
    @Override
    public void onCreated(final DashboardUserDO data) {
        publish(new UserCreatedEvent(data, SessionUtil.visitorName()));
    }
    
    @Override
    public void onUpdated(final DashboardUserDO data, final DashboardUserDO before) {
        publish(new UserUpdatedEvent(data, before, SessionUtil.visitorName()));
    }
    
    /**
     * on user deleted.
     *
     * @param users selectors
     */
    @Override
    public void onDeleted(final Collection<DashboardUserDO> users) {
        publish(new BatchUserDeletedEvent(users, SessionUtil.visitorName()));
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

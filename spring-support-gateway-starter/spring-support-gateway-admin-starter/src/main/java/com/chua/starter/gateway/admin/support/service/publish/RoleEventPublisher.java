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

import com.chua.starter.gateway.admin.support.model.entity.RoleDO;
import com.chua.starter.gateway.admin.support.model.event.AdminDataModelChangedEvent;
import com.chua.starter.gateway.admin.support.model.event.role.BatchRoleDeletedEvent;
import com.chua.starter.gateway.admin.support.model.event.role.RoleCreatedEvent;
import com.chua.starter.gateway.admin.support.model.event.role.RoleUpdatedEvent;
import com.chua.starter.gateway.admin.support.utils.SessionUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * RoleEventPublisher.
 */
@Component
public class RoleEventPublisher implements AdminDataModelChangedEventPublisher<RoleDO> {
    
    private final ApplicationEventPublisher publisher;
    
    public RoleEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    
    /**
     * on rule created.
     *
     * @param rule rule
     */
    @Override
    public void onCreated(final RoleDO rule) {
        publish(new RoleCreatedEvent(rule, SessionUtil.visitorName()));
    }
    
    
    /**
     * on rule updated.
     *
     * @param rule       rule
     * @param before     before rule
     * @param permission new permission
     */
    public void onUpdated(final RoleDO rule, final RoleDO before, final List<String> permission) {
        publish(new RoleUpdatedEvent(rule, before, SessionUtil.visitorName(), permission));
    }
    
    
    /**
     * role delete.
     *
     * @param roles data
     */
    @Override
    public void onDeleted(final Collection<RoleDO> roles) {
        publish(new BatchRoleDeletedEvent(roles, SessionUtil.visitorName()));
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

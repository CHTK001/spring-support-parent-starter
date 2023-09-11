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

package com.chua.starter.gateway.admin.support.model.event.user;

import com.chua.starter.gateway.admin.support.model.entity.DashboardUserDO;
import com.chua.starter.gateway.admin.support.model.enums.EventTypeEnum;

/**
 * UserUpdatedEvent.
 */
public class UserUpdatedEvent extends UserChangedEvent {
    
    
    /**
     * Create a new {@code UserUpdatedEvent}.operator is unknown.
     *
     * @param source   Current user state
     * @param before   before user state
     * @param operator operator
     */
    public UserUpdatedEvent(final DashboardUserDO source, final DashboardUserDO before, final String operator) {
        super(source, before, EventTypeEnum.USER_UPDATE, operator);
    }
    
}
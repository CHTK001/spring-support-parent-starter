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

package com.chua.starter.gateway.admin.support.model.query;

import com.chua.starter.gateway.admin.support.model.page.condition.BaseExcludedSearchCondition;
import com.chua.starter.gateway.admin.support.model.page.condition.SearchCondition;
import com.chua.starter.gateway.admin.support.model.page.condition.SwitchCondition;

/**
 * this is plugin query condition.
 */
public class PluginQueryCondition extends BaseExcludedSearchCondition implements SearchCondition, SwitchCondition {
    
    /**
     * search keyword: plugin name or role name.
     */
    private String keyword;
    
    /**
     * switch status: plugin status[close or open].
     */
    private Boolean switchStatus;
    
    
    /**
     * get switchStatus.
     *
     * @return status
     */
    @Override
    public Boolean getSwitchStatus() {
        return switchStatus;
    }
    
    /**
     * set switchStatus.
     *
     * @param switchStatus status
     */
    public void setSwitchStatus(final Boolean switchStatus) {
        this.switchStatus = switchStatus;
    }
    
    /**
     * get keyword.
     *
     * @return keyword
     */
    @Override
    public String getKeyword() {
        return keyword;
    }
    
    /**
     * set keyword.
     *
     * @param keyword keyword
     */
    @Override
    public void setKeyword(final String keyword) {
        this.keyword = keyword;
    }
}

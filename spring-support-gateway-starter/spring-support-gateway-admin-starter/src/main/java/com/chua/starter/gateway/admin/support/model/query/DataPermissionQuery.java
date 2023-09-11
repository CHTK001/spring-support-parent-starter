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

import com.chua.starter.gateway.admin.support.model.page.PageParameter;

import java.io.Serializable;
import java.util.Objects;

/**
 * data permission query.
 */
public class DataPermissionQuery implements Serializable {

    private static final long serialVersionUID = -2830562388349740181L;

    /**
     * user id.
     */
    private String userId;

    /**
     * page parameter.
     */
    private PageParameter pageParameter;

    public DataPermissionQuery() {
    }

    public DataPermissionQuery(final String userId, final PageParameter pageParameter) {
        this.userId = userId;
        this.pageParameter = pageParameter;
    }

    /**
     * Gets the value of userId.
     *
     * @return the value of userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the userId.
     *
     * @param userId userId
     */
    public void setUserId(final String userId) {
        this.userId = userId;
    }

    /**
     * Gets the value of pageParameter.
     *
     * @return the value of pageParameter
     */
    public PageParameter getPageParameter() {
        return pageParameter;
    }

    /**
     * Sets the pageParameter.
     *
     * @param pageParameter pageParameter
     */
    public void setPageParameter(final PageParameter pageParameter) {
        this.pageParameter = pageParameter;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataPermissionQuery)) {
            return false;
        }
        DataPermissionQuery that = (DataPermissionQuery) o;
        return Objects.equals(userId, that.userId) && Objects.equals(pageParameter, that.pageParameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, pageParameter);
    }
}

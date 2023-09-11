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

package com.chua.starter.gateway.admin.support.aspect;

import org.apache.commons.collections4.CollectionUtils;
import com.chua.starter.gateway.admin.support.aspect.annotation.DataPermission;
import com.chua.starter.gateway.admin.support.model.query.FilterQuery;
import com.chua.starter.gateway.admin.support.service.DataPermissionService;
import com.chua.starter.gateway.admin.support.utils.JwtUtils;
import org.apache.shenyu.common.exception.ShenyuException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

import static org.apache.shenyu.common.constant.AdminConstants.DATA_PERMISSION_RULE;
import static org.apache.shenyu.common.constant.AdminConstants.DATA_PERMISSION_SELECTOR;

/**
 * Data permission aop aspect.
 */
@Aspect
@Component
public class DataPermissionAspect {

    private final DataPermissionService dataPermissionService;

    public DataPermissionAspect(final DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
    }

    /**
     * define data permission aop point cut.
     */
    @Pointcut("@annotation(com.chua.starter.gateway.admin.support.aspect.annotation.DataPermission)")
    public void dataPermissionCut() { }


    /**
     * Real method processing around.
     *
     * @param point point {@link ProceedingJoinPoint}
     * @return result {@link Object}
     */
    @Around("dataPermissionCut()")
    public Object around(final ProceedingJoinPoint point) {
        // CHECKSTYLE:OFF
        try {
            return point.proceed(getFilterSQLData(point));
        } catch (Throwable throwable) {
            throw new ShenyuException(throwable);
        }
        // CHECKSTYLE:ON
    }

    /**
     * Organize SQL parameters with data permissions.
     *
     * @param point {@link ProceedingJoinPoint}
     * @return args {@link List}
     */
    private Object[] getFilterSQLData(final ProceedingJoinPoint point) {
        DataPermission dataPermission = ((MethodSignature) point.getSignature()).getMethod().getAnnotation(DataPermission.class);
        Object[] args = point.getArgs();
        if (dataPermission == null || args == null) {
            return args;
        }
        List<String> dataPermissionList = dataPermissionService.getDataPermission(JwtUtils.getUserInfo().getUserId());
        if (CollectionUtils.isEmpty(dataPermissionList)) {
            return args;
        }

        switch (dataPermission.dataType()) {
            case DATA_PERMISSION_SELECTOR:
            case DATA_PERMISSION_RULE:
                Stream.of(args)
                        .filter(FilterQuery.class::isInstance)
                        .forEach(q -> ((FilterQuery) q).setFilterIds(dataPermissionList));
                break;
            default:
                break;
        }
        return args;
    }
}
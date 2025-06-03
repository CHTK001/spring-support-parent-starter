/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.common.system;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 日志接口
 *
 * @author bwcx_jzy
 * @since 2019/4/19
 */
public interface AopLogInterface {
    /**
     * 进入前
     *
     * @param joinPoint point
     */
    void before(ProceedingJoinPoint joinPoint);

    /**
     * 执行后
     *
     * @param value 结果
     */
    void afterReturning(Object value);
}

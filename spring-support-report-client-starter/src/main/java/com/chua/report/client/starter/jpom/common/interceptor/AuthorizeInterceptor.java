/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.keepbx.jpom.model.JsonMessage;
import com.chua.report.client.starter.jpom.common.Const;
import com.chua.report.client.starter.jpom.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.configuration.AgentAuthorize;
import com.chua.report.client.starter.jpom.configuration.AgentConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

/**
 * 授权拦截
 *
 * @author bwcx_jzy
 * @since 2019/4/17
 */
@Configuration
public class AuthorizeInterceptor implements HandlerMethodInterceptor {

    private final AgentAuthorize agentAuthorize;

    public AuthorizeInterceptor(AgentConfig agentConfig) {
        this.agentAuthorize = agentConfig.getAuthorize();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
        NotAuthorize notAuthorize = handlerMethod.getMethodAnnotation(NotAuthorize.class);
        if (notAuthorize == null) {
            String authorize = JakartaServletUtil.getHeaderIgnoreCase(request, Const.JPOM_AGENT_AUTHORIZE);
            if (StrUtil.isEmpty(authorize)) {
                this.error(response);
                return false;
            }
            if (!agentAuthorize.checkAuthorize(authorize)) {
                this.error(response);
                return false;
            }
        }
        return true;
    }

    private void error(HttpServletResponse response) {
        JakartaServletUtil.write(response, JsonMessage.getString(Const.AUTHORIZE_ERROR, I18nMessageUtil.get("i18n.auth_info_error.c184")), MediaType.APPLICATION_JSON_VALUE);
    }
}

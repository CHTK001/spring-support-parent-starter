/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.common;

import com.chua.report.client.starter.jpom.agent.common.interceptor.AuthorizeInterceptor;
import com.chua.report.client.starter.jpom.common.common.validator.ParameterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author bwcx_jzy
 * @since 2022/12/8
 */
@Configuration
public class ClientWebConfigurer implements WebMvcConfigurer {

    private final ParameterInterceptor parameterInterceptor;
    private final AuthorizeInterceptor authorizeInterceptor;

    public ClientWebConfigurer(ParameterInterceptor parameterInterceptor,
                               AuthorizeInterceptor authorizeInterceptor) {
        this.parameterInterceptor = parameterInterceptor;
        this.authorizeInterceptor = authorizeInterceptor;
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(parameterInterceptor).addPathPatterns("/**");
        registry.addInterceptor(authorizeInterceptor).addPathPatterns("/**");
    }


}

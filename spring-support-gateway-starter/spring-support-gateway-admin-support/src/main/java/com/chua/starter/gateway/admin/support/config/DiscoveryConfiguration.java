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

package com.chua.starter.gateway.admin.support.config;

import com.chua.starter.gateway.admin.support.discovery.DefaultDiscoveryProcessor;
import com.chua.starter.gateway.admin.support.discovery.DiscoveryProcessor;
import com.chua.starter.gateway.admin.support.discovery.DiscoveryProcessorHolder;
import com.chua.starter.gateway.admin.support.discovery.LocalDiscoveryProcessor;
import com.chua.starter.gateway.admin.support.mapper.DiscoveryHandlerMapper;
import com.chua.starter.gateway.admin.support.mapper.DiscoveryUpstreamMapper;
import com.chua.starter.gateway.admin.support.mapper.ProxySelectorMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DiscoveryConfiguration.
 */
@Configuration
public class DiscoveryConfiguration {

    /**
     * discoveryProcessor.
     *
     * @param discoveryUpstreamMapper discoveryUpstreamMapper
     * @param discoveryHandlerMapper  discoveryHandlerMapper
     * @param proxySelectorMapper     proxySelectorMapper
     * @return DiscoveryProcessor
     */
    @Bean("DefaultDiscoveryProcessor")
    public DiscoveryProcessor discoveryDefaultProcessor(final DiscoveryUpstreamMapper discoveryUpstreamMapper,
                                                        final DiscoveryHandlerMapper discoveryHandlerMapper,
                                                        final ProxySelectorMapper proxySelectorMapper) {
        return new DefaultDiscoveryProcessor(discoveryUpstreamMapper, discoveryHandlerMapper, proxySelectorMapper);
    }

    /**
     * discoveryLocalProcessor.
     *
     * @param discoveryUpstreamMapper discoveryUpstreamMapper
     * @param proxySelectorMapper proxySelectorMapper
     * @return LocalDiscoveryProcessor
     */
    @Bean("LocalDiscoveryProcessor")
    public DiscoveryProcessor discoveryLocalProcessor(final DiscoveryUpstreamMapper discoveryUpstreamMapper, final ProxySelectorMapper proxySelectorMapper) {
        return new LocalDiscoveryProcessor(discoveryUpstreamMapper, proxySelectorMapper);
    }

    /**
     * discoveryProcessorHolder.
     *
     * @param defaultDiscoveryProcessor defaultDiscoveryProcessor
     * @param localDiscoveryProcessor   localDiscoveryProcessor
     * @return DiscoveryProcessorHolder
     */
    @Bean
    public DiscoveryProcessorHolder discoveryProcessorHolder(@Qualifier("DefaultDiscoveryProcessor") final DiscoveryProcessor defaultDiscoveryProcessor,
                                                             @Qualifier("LocalDiscoveryProcessor") final DiscoveryProcessor localDiscoveryProcessor
    ) {
        return new DiscoveryProcessorHolder(defaultDiscoveryProcessor, localDiscoveryProcessor);
    }

}

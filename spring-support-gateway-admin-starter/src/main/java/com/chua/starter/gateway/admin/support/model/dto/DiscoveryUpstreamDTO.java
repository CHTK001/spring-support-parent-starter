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

package com.chua.starter.gateway.admin.support.model.dto;

import org.apache.shenyu.admin.mapper.DiscoveryUpstreamMapper;
import org.apache.shenyu.admin.validation.annotation.Existed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * discovery upstream dto.
 */
public class DiscoveryUpstreamDTO implements Serializable {

    private static final long serialVersionUID = -1704110184910210095L;

    /**
     * id.
     */
    @Existed(provider = DiscoveryUpstreamMapper.class, nullOfIgnore = true, message = "discovery upstream not exited")
    private String id;

    /**
     * discoveryHandler id.
     */
    @NotBlank(message = "discoveryHandlerId不能为空")
    private String discoveryHandlerId;

    /**
     * protocol.
     */
    @NotBlank(message = "protocol不能为空")
    private String protocol;

    /**
     * url.
     */
    @NotBlank(message = "url不能为空")
    private String url;

    /**
     * status.
     */
    @NotNull(message = "status不能为空")
    private Integer status;

    /**
     * weight.
     */
    @NotNull(message = "weight不能为空")
    private Integer weight;

    /**
     * props.
     */
    @NotBlank(message = "props不能为空")
    private String props;

    /**
     * getId.
     *
     * @return id
     */
    public String getId() {

        return id;
    }

    /**
     * setId.
     *
     * @param id id
     */
    public void setId(final String id) {

        this.id = id;
    }

    /**
     * getDiscoveryHandlerId.
     *
     * @return discoveryHandlerId
     */
    public String getDiscoveryHandlerId() {

        return discoveryHandlerId;
    }

    /**
     * setDiscoveryHandlerId.
     *
     * @param discoveryHandlerId discoveryHandlerId
     */
    public void setDiscoveryHandlerId(final String discoveryHandlerId) {

        this.discoveryHandlerId = discoveryHandlerId;
    }

    /**
     * getProtocol.
     *
     * @return protocol
     */
    public String getProtocol() {

        return protocol;
    }

    /**
     * setProtocol.
     *
     * @param protocol protocol
     */
    public void setProtocol(final String protocol) {

        this.protocol = protocol;
    }

    /**
     * getUrl.
     *
     * @return url
     */
    public String getUrl() {

        return url;
    }

    /**
     * setUrl.
     *
     * @param url url
     */
    public void setUrl(final String url) {

        this.url = url;
    }

    /**
     * getStatus.
     *
     * @return status
     */
    public int getStatus() {
        return status;
    }

    /**
     * setStatus.
     *
     * @param status status
     */
    public void setStatus(final int status) {

        this.status = status;
    }

    /**
     * getWeight.
     *
     * @return weight
     */
    public int getWeight() {

        return weight;
    }

    /**
     * setWeight.
     *
     * @param weight weight
     */
    public void setWeight(final int weight) {

        this.weight = weight;
    }

    /**
     * getProps.
     *
     * @return props
     */
    public String getProps() {

        return props;
    }

    /**
     * setProps.
     *
     * @param props props
     */
    public void setProps(final String props) {

        this.props = props;
    }
}

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

package com.chua.starter.gateway.admin.support.service;

import com.chua.starter.gateway.admin.support.model.dto.FieldDTO;
import com.chua.starter.gateway.admin.support.model.page.CommonPager;
import com.chua.starter.gateway.admin.support.model.query.FieldQuery;
import com.chua.starter.gateway.admin.support.model.vo.FieldVO;
import com.chua.starter.gateway.admin.support.model.vo.PluginVO;

import java.util.List;

public interface FieldService {

    /**
     * Create or update string.
     *
     * @param apiDTO the api dto
     * @return the string
     */
    int createOrUpdate(FieldDTO apiDTO);

    /**
     * Delete by id.
     *
     * @param id the id
     * @return the string
     */
    int delete(String id);

    /**
     * deleteBatch by ids.
     * @param ids ids.
     * @return int
     */
    int deleteBatch(List<String> ids);

    /**
     * find api by id.
     *
     * @param id pk.
     * @return {@linkplain PluginVO}
     */
    FieldVO findById(String id);

    /**
     * find page of api by query.
     *
     * @param apiQuery {@linkplain FieldQuery}
     * @return {@linkplain CommonPager}
     */
    CommonPager<FieldVO> listByPage(FieldQuery apiQuery);

}
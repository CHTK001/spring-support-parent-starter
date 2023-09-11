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

package com.chua.starter.gateway.admin.support.controller;

import org.apache.commons.lang3.StringUtils;
import com.chua.starter.gateway.admin.support.mapper.ApiMapper;
import com.chua.starter.gateway.admin.support.model.dto.ApiDTO;
import com.chua.starter.gateway.admin.support.model.page.CommonPager;
import com.chua.starter.gateway.admin.support.model.page.PageParameter;
import com.chua.starter.gateway.admin.support.model.query.ApiQuery;
import com.chua.starter.gateway.admin.support.model.result.ShenyuAdminResult;
import com.chua.starter.gateway.admin.support.model.vo.ApiVO;
import com.chua.starter.gateway.admin.support.service.ApiService;
import com.chua.starter.gateway.admin.support.utils.ShenyuResultMessage;
import com.chua.starter.gateway.admin.support.validation.annotation.Existed;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * this is api controller.
 */
@Validated
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ApiService apiService;

    public ApiController(final ApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * query apis.
     *
     * @param apiPath     api path.
     * @param state       state.
     * @param tagId       tagId.
     * @param currentPage current page.
     * @param pageSize    page size.
     * @return {@linkplain ShenyuAdminResult}
     */
    @GetMapping("")
    public ShenyuAdminResult queryApis(final String apiPath, final Integer state,
                                       final String tagId,
                                       @NotNull final Integer currentPage,
                                       @NotNull final Integer pageSize) {
        CommonPager<ApiVO> commonPager = apiService.listByPage(new ApiQuery(apiPath, state, tagId, new PageParameter(currentPage, pageSize)));
        return ShenyuAdminResult.success(ShenyuResultMessage.QUERY_SUCCESS, commonPager);
    }

    /**
     * detail plugin.
     *
     * @param id plugin id.
     * @return {@linkplain ShenyuAdminResult}
     */
    @GetMapping("/{id}")
    public ShenyuAdminResult detailApi(@PathVariable("id")
                                       @Existed(message = "api is not existed",
                                               provider = ApiMapper.class) final String id) {
        ApiVO apiVO = apiService.findById(id);
        return ShenyuAdminResult.success(ShenyuResultMessage.DETAIL_SUCCESS, apiVO);
    }

    /**
     * create api.
     *
     * @param apiDTO api.
     * @return {@linkplain ShenyuAdminResult}
     */
    @PostMapping("")
    @RequiresPermissions("system:api:add")
    public ShenyuAdminResult createApi(@Valid @RequestBody final ApiDTO apiDTO) {
        return ShenyuAdminResult.success(apiService.createOrUpdate(apiDTO));
    }

    /**
     * update api.
     *
     * @param id     primary key.
     * @param apiDTO api.
     * @return {@linkplain ShenyuAdminResult}
     */
    @PutMapping("/{id}")
    @RequiresPermissions("system:api:edit")
    public ShenyuAdminResult updateApi(@PathVariable("id")
                                       @Existed(message = "api is not existed",
                                               provider = ApiMapper.class) final String id,
                                       @Valid @RequestBody final ApiDTO apiDTO) {
        apiDTO.setId(id);
        return ShenyuAdminResult.success(apiService.createOrUpdate(apiDTO));
    }

    /**
     * delete apis.
     *
     * @param ids primary key.
     * @return {@linkplain ShenyuAdminResult}
     */
    @DeleteMapping("/batch")
    @RequiresPermissions("system:api:delete")
    public ShenyuAdminResult deleteApis(@RequestBody @NotEmpty final List<@NotBlank String> ids) {
        final String result = apiService.delete(ids);
        if (StringUtils.isNoneBlank(result)) {
            return ShenyuAdminResult.error(result);
        }
        return ShenyuAdminResult.success(ShenyuResultMessage.DELETE_SUCCESS);
    }

}
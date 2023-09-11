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

import org.apache.commons.lang3.StringUtils;
import com.chua.starter.gateway.admin.support.exception.ShenyuAdminException;
import com.chua.starter.gateway.admin.support.model.dto.RuleConditionDTO;
import com.chua.starter.gateway.admin.support.model.dto.RuleDTO;
import com.chua.starter.gateway.admin.support.model.entity.RuleDO;
import com.chua.starter.gateway.admin.support.model.page.CommonPager;
import com.chua.starter.gateway.admin.support.model.query.RuleQuery;
import com.chua.starter.gateway.admin.support.model.query.RuleQueryCondition;
import com.chua.starter.gateway.admin.support.model.vo.RuleVO;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.enums.OperatorEnum;
import org.apache.shenyu.common.enums.ParamTypeEnum;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * this is rule service.
 */
public interface RuleService extends PageService<RuleQueryCondition, RuleVO> {

    /**
     * Register string.
     *
     * @param ruleDTO the rule dto
     * @return the string
     */
    String registerDefault(RuleDTO ruleDTO);

    /**
     * create or update rule.
     *
     * @param ruleDTO {@linkplain RuleDTO}
     * @return rows int
     */
    default int createOrUpdate(final RuleDTO ruleDTO) {

        // now, only check rule uri condition in pathPattern mode
        // todo check uri in other modes

        try {
            final List<RuleConditionDTO> ruleConditions = ruleDTO.getRuleConditions();
            ruleConditions.stream()
                    .filter(conditionData -> ParamTypeEnum.URI.getName().equals(conditionData.getParamType()))
                    .filter(conditionData -> OperatorEnum.PATH_PATTERN.getAlias().equals(conditionData.getOperator()))
                    .map(RuleConditionDTO::getParamValue)
                    .forEach(PathPatternParser.defaultInstance::parse);
        } catch (Exception e) {
            throw new ShenyuAdminException("uri validation of Condition failed, please check.", e);
        }
        return StringUtils.isBlank(ruleDTO.getId()) ? create(ruleDTO) : update(ruleDTO);
    }

    /**
     * create rule.
     *
     * @param ruleDTO {@linkplain RuleDTO}
     * @return rows int
     */
    int create(RuleDTO ruleDTO);

    /**
     * update rule.
     *
     * @param ruleDTO {@linkplain RuleDTO}
     * @return rows int
     */
    int update(RuleDTO ruleDTO);

    /**
     * delete rules.
     *
     * @param ids primary key.
     * @return rows int
     */
    int delete(List<String> ids);

    /**
     * find rule by id.
     *
     * @param id primary key.
     * @return {@linkplain RuleVO}
     */
    RuleVO findById(String id);

    /**
     * find page of rule by query.
     *
     * @param ruleQuery {@linkplain RuleQuery}
     * @return {@linkplain CommonPager}
     */
    CommonPager<RuleVO> listByPage(RuleQuery ruleQuery);

    /**
     * List all list.
     *
     * @return the list
     */
    List<RuleData> listAll();

    /**
     * Find by selector id list.
     *
     * @param selectorId the selector id
     * @return the list
     */
    List<RuleData> findBySelectorId(String selectorId);

    /**
     * Find by a list of selector ids.
     *
     * @param selectorIdList a list of selector ids
     * @return the list of RuleDatas
     */
    List<RuleData> findBySelectorIdList(List<String> selectorIdList);

    /**
     * Find rule by name.
     *
     * @param name rule's name.
     * @return {@link RuleDO}
     */
    RuleDO findByName(String name);
}

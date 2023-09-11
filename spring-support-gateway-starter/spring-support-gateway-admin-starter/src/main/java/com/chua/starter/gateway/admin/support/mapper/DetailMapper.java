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

package com.chua.starter.gateway.admin.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.chua.starter.gateway.admin.support.model.entity.DetailDO;
import com.chua.starter.gateway.admin.support.model.query.DetailQuery;
import com.chua.starter.gateway.admin.support.validation.ExistProvider;

import java.io.Serializable;
import java.util.List;

@Mapper
public interface DetailMapper extends ExistProvider {

    /**
     * existed.
     *
     * @param id id
     * @return existed
     */
    @Override
    Boolean existed(@Param("id") Serializable id);

    /**
     * insert detailDO to table.
     *
     * @param detailDO the detailDO
     * @return insert count
     */
    int insert(DetailDO detailDO);

    /**
     * insert detailDO to table selective.
     *
     * @param detailDO the detailDO
     * @return insert count
     */
    int insertSelective(DetailDO detailDO);

    /**
     * select by primary key.
     *
     * @param id primary key
     * @return object by primary key
     */
    DetailDO selectByPrimaryKey(String id);

    /**
     * update detailDO selective.
     *
     * @param detailDO the updated detailDO
     * @return update count
     */
    int updateByPrimaryKeySelective(DetailDO detailDO);

    /**
     * update detailDO.
     *
     * @param detailDO the updated detailDO
     * @return update count
     */
    int updateByPrimaryKey(DetailDO detailDO);

    /**
     * delete by primary key.
     *
     * @param id primaryKey
     * @return deleteCount
     */
    int deleteByPrimaryKey(String id);

    /**
     * selectByQuery.
     *
     * @param detailQuery detailQuery
     * @return DetailDOList
     */
    List<DetailDO> selectByQuery(DetailQuery detailQuery);

    /**
     * batchDelete.
     *
     * @param ids ids
     * @return int
     */
    int batchDelete(@Param("ids") List<String> ids);
}

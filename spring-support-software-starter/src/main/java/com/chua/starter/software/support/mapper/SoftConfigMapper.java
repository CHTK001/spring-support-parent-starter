package com.chua.starter.software.support.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.mybatis.mapper.BaseMapperX;
import com.chua.starter.software.support.entity.SoftConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 软件配置表 Mapper
 *
 * @author CH
 * @since 2026/7/25
 */
@Mapper
public interface SoftConfigMapper extends BaseMapperX<SoftConfig> {

    /**
     * 分页查询配置列表
     *
     * @param page 分页参数
     * @param request 查询条件
     * @return 分页结果
     */
    Page<SoftConfig> selectPageFor(
            @Param("page") Page<SoftConfig> page,
            @Param("request") SoftConfig request
    );
}
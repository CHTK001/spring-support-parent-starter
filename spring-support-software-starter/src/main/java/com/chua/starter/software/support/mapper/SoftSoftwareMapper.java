package com.chua.starter.software.support.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.mybatis.mapper.BaseMapperX;
import com.chua.starter.software.support.entity.SoftSoftware;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 软件信息表 Mapper
 *
 * @author CH
 * @since 2026/7/25
 */
@Mapper
public interface SoftSoftwareMapper extends BaseMapperX<SoftSoftware> {

    /**
     * 分页查询软件列表
     *
     * @param page 分页参数
     * @param request 查询条件
     * @param installedIds 已安装软件ID集合
     * @return 分页结果
     */
    Page<SoftSoftware> selectPageFor(
            @Param("page") Page<SoftSoftware> page,
            @Param("request") SoftSoftware request,
            @Param("installedIds") java.util.Collection<String> installedIds
    );

    /**
     * 根据源ID批量更新软件状态
     *
     * @param sourceId 源ID
     * @param status   新状态
     * @return 更新行数
     */
    int updateStatusBySourceId(@Param("sourceId") String sourceId, @Param("status") Integer status);
}
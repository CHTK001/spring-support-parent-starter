package com.chua.starter.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.entity.project.SysProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysProjectMapper extends BaseMapper<SysProject> {
    /**
     * 分页查询
     *
     * @param page       分页
     * @param sysProject 条件
     * @return 结果
     */
    IPage<SysProject> selectPageFor(
            @Param("page") Page<SysProject> page,
            @Param("request") SysProject sysProject);

    /**
     * 获取默认项目
     *
     * @param typeName 类型
     * @return 项目
     */
    IPage<SysProject> getDefaultByType(@Param("page") Page<SysProject> page, @Param("typeName") String typeName);

    /**
     * 获取项目
     *
     * @param sysProjectId 项目ID
     * @return 项目
     */
    default SysProject getOne(Integer sysProjectId) {
        SysProject sysProject = new SysProject();
        sysProject.setSysProjectId(sysProjectId);
        IPage<SysProject> sysProjectIPage = selectPageFor(new Page<>(1, 1), sysProject);
        if (sysProjectIPage.getTotal() > 0) {
            return sysProjectIPage.getRecords().get(0);
        }
        return null;
    }
}
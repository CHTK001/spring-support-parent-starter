package com.chua.starter.template.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.model.TemplatePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模板信息 映射器
 *
 * <p>简单单表 CRUD 走 MyBatis-Plus；分页、列表、统计这类多条件 SQL
 * 放在 {@code mapper/template/TemplateInfoMapper.xml}，避免在 Service 里堆
 * {@code LambdaQueryWrapper} 条件，便于 DBA 审核与执行计划调优。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface TemplateInfoMapper extends BaseMapper<TemplateInfo> {

    /**
     * 模板分页查询（XML）
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<TemplateInfo> selectTemplatePage(IPage<TemplateInfo> page,
                                           @Param("q") TemplatePageQuery query);

    /**
     * 模板列表（XML）
     *
     * @param query 查询条件
     * @return 模板列表
     */
    List<TemplateInfo> selectTemplateList(TemplatePageQuery query);

    /**
     * 模板统计（XML）
     *
     * @param query 查询条件
     * @return 模板总数
     */
    Long selectTemplateCount(TemplatePageQuery query);
}

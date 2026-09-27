package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduInsectGuide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 昆虫深度讲解与测验 Mapper
 *
 * <p>CRUD 走 MyBatis-Plus BaseMapper；按 slug 取单条走自定义 XML 的 {@code SELECT *}，
 * 原因与 EduInsectMapper 相同：本库表未落 SysBase 的 create_name/create_by 等列，
 * 用 selectOne/QueryWrapper 会拼出这些不存在的列。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduInsectGuideMapper extends BaseMapper<EduInsectGuide> {

    /**
     * 按物种标识取讲解
     *
     * @param insectCode 物种英文标识（slug）
     * @return 讲解，不存在时为 null
     */
    EduInsectGuide selectByCode(@Param("insectCode") String insectCode);

    /**
     * 取全部已有讲解的物种标识
     *
     * @param userId 归属隔离：非空时只统计系统讲解与该用户的讲解
     * @return 物种 slug 列表
     */
    java.util.List<String> selectAllCodes(@Param("userId") String userId);

    /**
     * 按物种标识更新讲解
     *
     * <p>不用 BaseMapper.updateById：它按实体拼列清单，会带上本库不存在的
     * SysBase 列（create_name / create_by / update_name / update_by）而报错。
     * 这里显式只写本表真实存在的列。</p>
     *
     * @param row 讲解实体，{@code insectCode} 必填
     * @return 影响行数
     */
    int updateByCode(EduInsectGuide row);
}

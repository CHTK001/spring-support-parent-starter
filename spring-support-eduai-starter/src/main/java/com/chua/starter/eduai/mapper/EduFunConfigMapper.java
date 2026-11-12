package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduFunConfig;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 趣味模块配置 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduFunConfigMapper extends BaseMapper<EduFunConfig> {

    /**
     * 已启用的趣味模块配置列表（XML 语句 selectEnabledList，按排序号升序）。
     *
     * @param scope 归属隔离参数
     * @return 配置列表
     */
    List<EduFunConfig> selectEnabledList(@Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 全部趣味模块配置列表（XML 语句 selectScopedList，含停用，按排序号升序）。
     *
     * @param scope 归属隔离参数
     * @return 配置列表
     */
    List<EduFunConfig> selectScopedList(@Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 按模块编码查询配置（XML 语句 selectByModuleCode，取第一条）。
     *
     * @param moduleCode 模块编码
     * @param gradeLevel 学段；为空时不限（优先通用行）
     * @param scope      归属隔离参数
     * @return 配置，不存在时返回 {@code null}
     */
    EduFunConfig selectByModuleCode(@Param("moduleCode") String moduleCode,
                                    @Param("gradeLevel") String gradeLevel,
                                    @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 查询指定学段启用中的模块清单（学生端首页用）
     *
     * @param gradeLevel 学段：PRIMARY / JUNIOR / SENIOR
     * @param scope      归属隔离参数
     * @return 该学段的模块列表，按 sort_order 升序
     */
    List<EduFunConfig> selectByLevel(@Param("gradeLevel") String gradeLevel,
                                     @Param("scope") EduUserContext.OwnerScope scope);
}

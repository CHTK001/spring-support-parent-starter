package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduSubject;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学科维度 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduSubjectMapper extends BaseMapper<EduSubject> {

    /**
     * 列表学科（XML 语句 listSubjects：可选包含停用 + 学段模糊匹配 + 归属隔离，按排序号升序）。
     *
     * @param gradeLevel      学段，为空时不限学段
     * @param includeDisabled 为 true 时包含停用学科（管理端全量列表），否则仅启用学科（下拉等场景）
     * @param scope           归属隔离参数
     * @return 学科列表
     */
    List<EduSubject> listSubjects(@Param("gradeLevel") String gradeLevel,
                                  @Param("includeDisabled") Boolean includeDisabled,
                                  @Param("scope") EduUserContext.OwnerScope scope);
}

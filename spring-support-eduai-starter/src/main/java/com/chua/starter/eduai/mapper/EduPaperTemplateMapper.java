package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduPaperTemplate;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组卷模板 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduPaperTemplateMapper extends BaseMapper<EduPaperTemplate> {

    /**
     * 分页查询组卷模板（XML 语句 pageSelect）。
     *
     * @param page   分页参数
     * @param name   模板名称，模糊匹配，可为空
     * @param style  大标题风格，精确匹配，可为空
     * @param status 状态，可为空
     * @param scope  归属隔离参数
     * @return 组卷模板分页
     */
    IPage<EduPaperTemplate> pageSelect(Page<EduPaperTemplate> page,
                                       @Param("name") String name,
                                       @Param("style") String style,
                                       @Param("status") Integer status,
                                       @Param("scope") EduUserContext.OwnerScope scope);
}

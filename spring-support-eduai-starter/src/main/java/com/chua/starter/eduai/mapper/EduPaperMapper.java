package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 试卷 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduPaperMapper extends BaseMapper<EduPaper> {

    /**
     * 分页查询试卷（XML 语句 pageSelect）。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @param scope 归属隔离参数
     * @return 试卷分页
     */
    IPage<EduPaper> pageSelect(Page<EduPaper> page, @Param("query") PaperQueryDTO query,
                                @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 按试卷唯一编号取试卷（XML 语句 selectByPaperNo，生成编号时查重）。
     *
     * @param paperNo 试卷唯一编号
     * @return 试卷实体，不存在返回 null
     */
    EduPaper selectByPaperNo(@Param("paperNo") String paperNo);
}

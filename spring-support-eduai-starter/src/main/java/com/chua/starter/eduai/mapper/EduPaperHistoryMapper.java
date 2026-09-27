package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduPaperHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 试卷历史 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduPaperHistoryMapper extends BaseMapper<EduPaperHistory> {

    /**
     * 取试卷的历史列表（XML 语句 selectByPaperId，按版本倒序，不取 snapshot 大字段）。
     *
     * @param paperId 试卷标识
     * @return 历史列表（snapshot 为空）
     */
    List<EduPaperHistory> selectByPaperId(@Param("paperId") Long paperId);
}

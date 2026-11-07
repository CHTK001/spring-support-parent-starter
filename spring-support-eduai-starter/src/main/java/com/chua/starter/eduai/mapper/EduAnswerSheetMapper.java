package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduAnswerSheet;
import org.apache.ibatis.annotations.Mapper;
/**
 * 答题卡 Mapper。
 *
 * @author CH
 * @since 4.0.0.51
 */

@Mapper
public interface EduAnswerSheetMapper extends BaseMapper<EduAnswerSheet> {
}

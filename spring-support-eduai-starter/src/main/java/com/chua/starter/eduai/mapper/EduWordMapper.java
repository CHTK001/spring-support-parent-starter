package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 词语库 Mapper。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface EduWordMapper extends BaseMapper<EduWord> {
}

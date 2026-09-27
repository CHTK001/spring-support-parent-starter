package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduPoem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 古诗 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduPoemMapper extends BaseMapper<EduPoem> {
}

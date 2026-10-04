package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduHanzi;
import org.apache.ibatis.annotations.Mapper;

/**
 * 汉字基础数据 Mapper。
 *
 * @since 4.0.0.43
 * @author CH
 */
@Mapper
public interface EduHanziMapper extends BaseMapper<EduHanzi> {
}

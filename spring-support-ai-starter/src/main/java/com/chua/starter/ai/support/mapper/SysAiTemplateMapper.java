package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模板 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiTemplateMapper extends BaseMapper<SysAiTemplate> {
}

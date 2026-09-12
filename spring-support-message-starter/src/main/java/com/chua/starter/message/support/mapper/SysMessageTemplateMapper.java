package com.chua.starter.message.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
* 消息模板 映射器
*
* @since 2026/08/03
* @author CH
 */
@Mapper
public interface SysMessageTemplateMapper extends BaseMapper<SysMessageTemplate> {
}

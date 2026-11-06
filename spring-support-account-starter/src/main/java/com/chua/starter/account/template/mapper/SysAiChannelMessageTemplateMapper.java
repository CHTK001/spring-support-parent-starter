package com.chua.starter.account.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.template.store.SysAiChannelMessageTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信 / 邮件消息模板映射器。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface SysAiChannelMessageTemplateMapper extends BaseMapper<SysAiChannelMessageTemplate> {
}

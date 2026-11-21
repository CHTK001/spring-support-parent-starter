package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiAlertRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 告警规则触发留痕 映射器。
 *
 * @since 4.0.0.62
 * @author CH
 */
@Mapper
public interface SysAiAlertRecordMapper extends BaseMapper<SysAiAlertRecord> {
}

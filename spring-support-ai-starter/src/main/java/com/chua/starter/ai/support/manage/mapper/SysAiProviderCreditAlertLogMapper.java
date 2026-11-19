package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderCreditAlertLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商余额告警推送留痕 映射器。
 *
 * @since 4.0.0.59
 * @author CH
 */
@Mapper
public interface SysAiProviderCreditAlertLogMapper extends BaseMapper<SysAiProviderCreditAlertLog> {
}

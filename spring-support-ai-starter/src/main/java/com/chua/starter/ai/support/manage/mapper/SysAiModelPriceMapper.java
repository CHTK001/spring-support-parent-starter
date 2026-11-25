package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelPrice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型价格库 映射器。
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface SysAiModelPriceMapper extends BaseMapper<SysAiModelPrice> {
}

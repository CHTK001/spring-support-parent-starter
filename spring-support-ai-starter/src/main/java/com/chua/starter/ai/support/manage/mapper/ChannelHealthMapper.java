package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.ChannelHealth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道健康探针记录映射器。
 *
 * @author CH
 * @since 4.0.0.57
 */
@Mapper
public interface ChannelHealthMapper extends BaseMapper<ChannelHealth> {
}

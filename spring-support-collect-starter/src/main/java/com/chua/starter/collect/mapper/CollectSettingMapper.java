package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集平台运行参数 Mapper。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface CollectSettingMapper extends BaseMapper<CollectSetting> {
}

package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集执行通知 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectNoticeMapper extends BaseMapper<CollectNotice> {
}

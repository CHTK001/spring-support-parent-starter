package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运行时快照 Mapper
 */
@Mapper
public interface SpiderRuntimeSnapshotMapper extends BaseMapper<SpiderRuntimeSnapshot> {
}

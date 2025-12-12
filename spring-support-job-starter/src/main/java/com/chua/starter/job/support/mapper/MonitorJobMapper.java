package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.MonitorJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务Mapper接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Mapper
public interface MonitorJobMapper extends BaseMapper<MonitorJob> {

}

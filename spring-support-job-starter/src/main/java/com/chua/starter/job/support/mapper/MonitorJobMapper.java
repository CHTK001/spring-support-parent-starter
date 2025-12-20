package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.MonitorJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务Mapper接口
 * <p>
 * 提供定时任务的数据库操作，继承自MyBatis-Plus的{@link BaseMapper}。
 * 支持任务的增删改查、分页查询等操作。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see MonitorJob
 */
@Mapper
public interface MonitorJobMapper extends BaseMapper<MonitorJob> {

}

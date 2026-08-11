package com.chua.starter.taskdistribution.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.taskdistribution.support.entity.SysTaskStore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务存储 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface TaskStoreMapper extends BaseMapper<SysTaskStore> {
}
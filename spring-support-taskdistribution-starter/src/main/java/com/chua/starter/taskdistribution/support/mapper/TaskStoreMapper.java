package com.chua.starter.taskdistribution.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.taskdistribution.support.entity.SysTaskStore;
import org.apache.ibatis.annotations.Mapper;

/**
   * 任务存储 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface TaskStoreMapper extends BaseMapper<SysTaskStore> {
}
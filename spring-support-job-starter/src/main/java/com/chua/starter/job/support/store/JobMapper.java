package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_作业 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {

    /**
     * 按分组查询有效任务编号列表。
     *
     * @param group 任务分组
     * @return 任务编号列表
     * @param @Param("group" @参数("群体"
     */
    List<String> selectJobNosByGroup(@Param("group") String group);
}

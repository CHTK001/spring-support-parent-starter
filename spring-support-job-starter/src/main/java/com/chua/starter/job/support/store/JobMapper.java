package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
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

    /**
     * 取数据库当前时刻（毫秒）。
     *
     * <p>调度判据（是否到点、是否错过）必须用同一个时钟，多机部署下各节点本机时钟漂移
     * 会把触发相位整体平移，故统一以数据库时钟为准。</p>
     *
     * @return 数据库当前时刻（调用方换算毫秒；SQL 内不含库专用时间函数）
     */
    Timestamp selectDbNow();
}

package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.MonitorJobLogDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志详情 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorJobLogDetailMapper extends BaseMapper<MonitorJobLogDetail> {

    /**
     * 根据任务日志ID查询详情列表
     *
     * @param jobLogId 任务日志ID
     * @return 详情列表
     */
    @Select("SELECT * FROM monitor_job_log_detail WHERE job_log_id = #{jobLogId} ORDER BY job_log_detail_time ASC")
    List<MonitorJobLogDetail> selectByJobLogId(@Param("jobLogId") Integer jobLogId);

    /**
     * 根据任务ID查询详情列表
     *
     * @param jobId 任务ID
     * @return 详情列表
     */
    @Select("SELECT * FROM monitor_job_log_detail WHERE job_id = #{jobId} ORDER BY job_log_detail_time DESC LIMIT 1000")
    List<MonitorJobLogDetail> selectByJobId(@Param("jobId") Integer jobId);

    /**
     * 删除指定时间之前的日志详情
     *
     * @param beforeTime 截止时间
     * @return 删除数量
     */
    @Delete("DELETE FROM monitor_job_log_detail WHERE job_log_detail_time < #{beforeTime}")
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 删除指定任务日志ID的所有详情
     *
     * @param jobLogId 任务日志ID
     * @return 删除数量
     */
    @Delete("DELETE FROM monitor_job_log_detail WHERE job_log_id = #{jobLogId}")
    int deleteByJobLogId(@Param("jobLogId") Integer jobLogId);

    /**
     * 统计指定时间范围内的日志详情数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志数量
     */
    @Select("SELECT COUNT(*) FROM monitor_job_log_detail WHERE job_log_detail_time BETWEEN #{startTime} AND #{endTime}")
    long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}

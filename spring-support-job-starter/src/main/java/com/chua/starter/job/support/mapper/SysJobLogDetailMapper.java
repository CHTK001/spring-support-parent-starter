package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.SysJobLogDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志详情Mapper接口
 * <p>
 * 提供任务执行过程中详细日志的数据库操作。
 * 支持按任务日志ID、任务ID查询，以及日志清理等操作。
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 * @see SysJobLogDetail
 */
@Mapper
public interface SysJobLogDetailMapper extends BaseMapper<SysJobLogDetail> {

    /**
     * 根据任务日志ID查询详情列表
     *
     * @param jobLogId 任务日志ID
     * @return 详情列表
     */
    @Select("SELECT * FROM sys_job_log_detail WHERE job_log_id = #{jobLogId} ORDER BY job_log_detail_time ASC")
    List<SysJobLogDetail> selectByJobLogId(@Param("jobLogId") Integer jobLogId);

    /**
     * 根据任务ID查询详情列表
     *
     * @param jobId 任务ID
     * @return 详情列表
     */
    @Select("SELECT * FROM sys_job_log_detail WHERE job_id = #{jobId} ORDER BY job_log_detail_time DESC LIMIT 1000")
    List<SysJobLogDetail> selectByJobId(@Param("jobId") Integer jobId);

    /**
     * 删除指定时间之前的日志详情
     *
     * @param beforeTime 截止时间
     * @return 删除数量
     */
    @Delete("DELETE FROM sys_job_log_detail WHERE job_log_detail_time < #{beforeTime}")
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 删除指定任务日志ID的所有详情
     *
     * @param jobLogId 任务日志ID
     * @return 删除数量
     */
    @Delete("DELETE FROM sys_job_log_detail WHERE job_log_id = #{jobLogId}")
    int deleteByJobLogId(@Param("jobLogId") Integer jobLogId);

    /**
     * 统计指定时间范围内的日志详情数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志数量
     */
    @Select("SELECT COUNT(*) FROM sys_job_log_detail WHERE job_log_detail_time BETWEEN #{startTime} AND #{endTime}")
    long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}

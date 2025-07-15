package com.chua.starter.monitor.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScriptExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 脚本执行历史Mapper
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSysGenScriptExecutionMapper extends BaseMapper<MonitorSysGenScriptExecution> {

    /**
     * 根据脚本ID查找执行历史
     */
    @Select("SELECT * FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_id = #{scriptId} " +
            "ORDER BY monitor_sys_gen_script_execution_start_time DESC " +
            "LIMIT #{limit}")
    List<MonitorSysGenScriptExecution> findByScriptId(@Param("scriptId") Integer scriptId, @Param("limit") Integer limit);

    /**
     * 根据执行状态查找执行历史
     */
    @Select("SELECT * FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_status = #{status} " +
            "ORDER BY monitor_sys_gen_script_execution_start_time DESC")
    List<MonitorSysGenScriptExecution> findByStatus(@Param("status") String status);

    /**
     * 查找正在运行的执行记录
     */
    @Select("SELECT * FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_status = 'RUNNING' " +
            "ORDER BY monitor_sys_gen_script_execution_start_time DESC")
    List<MonitorSysGenScriptExecution> findRunningExecutions();

    /**
     * 根据进程ID查找执行记录
     */
    @Select("SELECT * FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_process_id = #{processId}")
    MonitorSysGenScriptExecution findByProcessId(@Param("processId") Long processId);

    /**
     * 查找指定时间范围内的执行记录
     */
    @Select("SELECT * FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_start_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY monitor_sys_gen_script_execution_start_time DESC")
    List<MonitorSysGenScriptExecution> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 获取脚本执行统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as totalExecutions, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'FAILED' THEN 1 ELSE 0 END) as failedCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'TIMEOUT' THEN 1 ELSE 0 END) as timeoutCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'RUNNING' THEN 1 ELSE 0 END) as runningCount, " +
            "AVG(monitor_sys_gen_script_execution_duration) as avgDuration " +
            "FROM monitor_sys_gen_script_execution")
    ExecutionStatistics getExecutionStatistics();

    /**
     * 获取指定脚本的执行统计
     */
    @Select("SELECT " +
            "COUNT(*) as totalExecutions, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'FAILED' THEN 1 ELSE 0 END) as failedCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'TIMEOUT' THEN 1 ELSE 0 END) as timeoutCount, " +
            "AVG(monitor_sys_gen_script_execution_duration) as avgDuration, " +
            "MAX(monitor_sys_gen_script_execution_start_time) as lastExecuteTime " +
            "FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_id = #{scriptId}")
    ScriptExecutionStatistics getScriptExecutionStatistics(@Param("scriptId") Integer scriptId);

    /**
     * 获取每日执行统计
     */
    @Select("SELECT " +
            "DATE(monitor_sys_gen_script_execution_start_time) as executeDate, " +
            "COUNT(*) as totalCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_execution_status = 'FAILED' THEN 1 ELSE 0 END) as failedCount " +
            "FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_start_time >= #{startDate} " +
            "GROUP BY DATE(monitor_sys_gen_script_execution_start_time) " +
            "ORDER BY executeDate DESC")
    List<DailyExecutionStatistics> getDailyExecutionStatistics(@Param("startDate") LocalDateTime startDate);

    /**
     * 清理过期的执行记录
     */
    @Select("DELETE FROM monitor_sys_gen_script_execution " +
            "WHERE monitor_sys_gen_script_execution_start_time < #{expireTime}")
    int cleanExpiredExecutions(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 执行统计信息
     */
    interface ExecutionStatistics {
        Long getTotalExecutions();
        Long getSuccessCount();
        Long getFailedCount();
        Long getTimeoutCount();
        Long getRunningCount();
        Double getAvgDuration();
    }

    /**
     * 脚本执行统计
     */
    interface ScriptExecutionStatistics {
        Long getTotalExecutions();
        Long getSuccessCount();
        Long getFailedCount();
        Long getTimeoutCount();
        Double getAvgDuration();
        LocalDateTime getLastExecuteTime();
    }

    /**
     * 每日执行统计
     */
    interface DailyExecutionStatistics {
        String getExecuteDate();
        Long getTotalCount();
        Long getSuccessCount();
        Long getFailedCount();
    }
}

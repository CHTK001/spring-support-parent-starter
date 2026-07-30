package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.SysJobLogBackup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务日志备份Mapper接口
 * <p>
 * 提供任务日志备份记录的数据库操作。
 * 支持查询最近备份、运行中备份、统计备份大小等操作。
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 * @see SysJobLogBackup
 */
@Mapper
public interface SysJobLogBackupMapper extends BaseMapper<SysJobLogBackup> {

    /**
     * 查询最近的备份记录
     *
     * @param limit 限制数量
     * @return 备份记录列表
     */
    @Select("SELECT * FROM sys_job_log_backup ORDER BY job_log_backup_start_time DESC LIMIT #{limit}")
    List<SysJobLogBackup> selectRecent(@Param("limit") int limit);

    /**
     * 查询正在运行的备份
     *
     * @return 正在运行的备份
     */
    @Select("SELECT * FROM sys_job_log_backup WHERE job_log_backup_status = 'RUNNING' ORDER BY job_log_backup_start_time DESC LIMIT 1")
    SysJobLogBackup selectRunning();

    /**
     * 统计备份文件总大小
     *
     * @return 总大小(字节)
     */
    @Select("SELECT COALESCE(SUM(job_log_backup_file_size), 0) FROM sys_job_log_backup WHERE job_log_backup_status = 'SUCCESS'")
    long sumBackupSize();

    /**
     * 统计备份日志总条数
     *
     * @return 总条数
     */
    @Select("SELECT COALESCE(SUM(job_log_backup_count), 0) FROM sys_job_log_backup WHERE job_log_backup_status = 'SUCCESS'")
    long sumBackupCount();
}

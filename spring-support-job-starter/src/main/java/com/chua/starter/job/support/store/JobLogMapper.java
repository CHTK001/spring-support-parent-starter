package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;

/**
 * sys_job_log Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface JobLogMapper extends BaseMapper<JobLogEntity> {

    /**
     * 批量删除指定时间之前的日志记录。
     *
     * @param beforeTimestamp 时间戳毫秒
     * @return 删除记录数
     */
    @Delete("DELETE FROM sys_job_log WHERE sys_job_log_trigger_time &lt;= #{beforeTimestamp}")
    int deleteByTriggerTimeBefore(long beforeTimestamp);
}

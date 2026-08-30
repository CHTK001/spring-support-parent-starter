package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * sys_job_log Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface JobLogMapper extends BaseMapper<JobLogEntity> {

    /**
     * 批量删除指定时间之前的日志记录。
     *
     * @param beforeTime 截止时间 (Oracle DATE 列)
     * @return 删除记录数
     */
    int deleteByTriggerTimeBefore(@Param("beforeTime") Date beforeTime);
}

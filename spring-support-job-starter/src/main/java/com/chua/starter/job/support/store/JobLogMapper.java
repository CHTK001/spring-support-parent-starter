package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
* sys_作业_日志 映射器。
*
* @since 4.0.0.42
* @author CH
 */
public interface JobLogMapper extends BaseMapper<JobLogEntity> {

    /**
    * 批量删除指定时间之前的日志记录。
    *
    * @param beforeTime 截止时间 (Oracle 日期 列)
    * @return 删除记录数
    * @param @Param("beforeTime" @参数("之前时间"
     */
    int deleteByTriggerTimeBefore(@Param("beforeTime") Date beforeTime);
}

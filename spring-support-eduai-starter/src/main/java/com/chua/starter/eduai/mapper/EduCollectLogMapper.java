package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduCollectLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题库采集执行日志 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduCollectLogMapper extends BaseMapper<EduCollectLog> {
}

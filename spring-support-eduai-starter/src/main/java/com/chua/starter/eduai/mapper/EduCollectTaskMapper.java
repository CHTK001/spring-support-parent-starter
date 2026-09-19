package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduCollectTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题库采集任务 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduCollectTaskMapper extends BaseMapper<EduCollectTask> {
}

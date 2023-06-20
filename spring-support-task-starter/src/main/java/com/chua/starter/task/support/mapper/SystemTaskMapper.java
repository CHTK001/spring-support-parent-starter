package com.chua.starter.task.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.task.support.pojo.SysTask;
import org.apache.ibatis.annotations.Mapper;

/**
 *    
 * @author CH
 */     
@Mapper
public interface SystemTaskMapper extends BaseMapper<SysTask> {
}
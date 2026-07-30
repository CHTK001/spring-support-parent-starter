package com.chua.starter.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.entity.project.SysSmsLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信日志 Mapper
 *
 * @author CH
 * @since 2024/11/27
 */
@Mapper
public interface SysSmsLogMapper extends BaseMapper<SysSmsLog> {
}

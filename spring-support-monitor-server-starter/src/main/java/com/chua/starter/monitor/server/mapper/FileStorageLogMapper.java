package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.server.entity.FileStorageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author CH
 * @since 2024/7/22
 */
@Mapper
public interface FileStorageLogMapper extends BaseMapper<FileStorageLog> {
}
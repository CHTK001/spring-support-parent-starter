package com.chua.starter.filesystem.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件系统分片上传任务Mapper。
 *
 * @author CH
 * @since 1.0
 */
@Mapper
public interface SysFileSystemUploadMapper extends BaseMapper<SysFileSystemUpload> {
}
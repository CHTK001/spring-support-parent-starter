package com.chua.starter.filesystem.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件系统分片上传详情映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysFileSystemUploadChunkMapper extends BaseMapper<SysFileSystemUploadChunk> {
}

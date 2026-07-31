package com.chua.starter.filesystem.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件系统上传完成主体Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysFileSystemUploadFileMapper extends BaseMapper<SysFileSystemUploadFile> {
}

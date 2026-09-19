package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.MsServerRelease;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布管理 Mapper
 *
 * @author CH
 */
@Mapper
public interface MsServerReleaseMapper extends BaseMapper<MsServerRelease> {
}

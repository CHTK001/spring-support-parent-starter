package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.MsServerProject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目管理 Mapper
 *
 * @author CH
 */
@Mapper
public interface MsServerProjectMapper extends BaseMapper<MsServerProject> {
}

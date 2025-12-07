package com.chua.tenant.support.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.tenant.support.common.entity.SysServiceModuleItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 服务模块关联Mapper接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Mapper
public interface SysServiceModuleItemMapper extends BaseMapper<SysServiceModuleItem> {
}

package com.chua.tenant.support.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.tenant.support.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}

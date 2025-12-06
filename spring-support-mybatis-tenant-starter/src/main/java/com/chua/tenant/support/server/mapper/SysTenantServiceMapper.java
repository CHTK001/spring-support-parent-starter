package com.chua.tenant.support.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.tenant.support.common.entity.SysTenantService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户服务 Mapper 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Mapper
public interface SysTenantServiceMapper extends BaseMapper<SysTenantService> {

    /**
     * 根据租户ID获取菜单ID列表
     *
     * @param tenantId 租户ID
     * @return 菜单ID列表
     */
    List<Integer> getMenuByTenantId(@Param("tenantId") Integer tenantId);
}

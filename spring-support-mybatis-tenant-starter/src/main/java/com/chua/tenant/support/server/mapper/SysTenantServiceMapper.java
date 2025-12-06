package com.chua.tenant.support.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.tenant.support.common.entity.SysTenantService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("""
            SELECT sm.sys_menu_id 
            FROM sys_tenant_service sts 
            JOIN sys_service_menu ssm ON sts.sys_service_id = ssm.sys_service_id 
            JOIN sys_menu sm ON ssm.sys_menu_id = sm.sys_menu_id 
            WHERE sts.sys_tenant_id = #{tenantId}
            """)
    List<Integer> getMenuByTenantId(@Param("tenantId") Integer tenantId);
}

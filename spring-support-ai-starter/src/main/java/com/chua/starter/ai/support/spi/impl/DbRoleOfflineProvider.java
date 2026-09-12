package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.ai.support.spi.RoleOfflineProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 默认本地角色离线 Provider：从数据库 {@code sys_ai_role} 表读取启用角色。
*
* <p>作为 Spring Bean 注册，确保可注入 {@link SysAiRoleService}；
* 同时由配置控制器通过 Spring 注入枚举。</p>
*
* @since 4.0.0.43
* @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbRoleOfflineProvider implements RoleOfflineProvider {

    private final SysAiRoleService roleService;

    @Override
    public String name() {
        return "本地数据库角色";
    }

    @Override
    public List<Map<String, Object>> listRoles() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<SysAiRole> roles = roleService.listEnabled();
            if (roles == null) {
                return result;
            }
            for (SysAiRole role : roles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", role.getCode());
                item.put("name", role.getName());
                item.put("description", role.getDescription());
                item.put("model", role.getModel());
                item.put("temperature", role.getTemperature());
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("[RoleSPI] 读取本地数据库角色失败: {}", e.getMessage());
        }
        return result;
    }
}

package com.chua.tenant.support.server.sync;

import com.chua.sync.support.server.SyncServer;
import com.chua.tenant.support.common.entity.SysTenant;
import com.chua.tenant.support.common.properties.TenantProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户服务端同步发布器
 * <p>
 * 服务端模式下，用于向客户端推送租户数据变更
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Component
@ConditionalOnBean(SyncServer.class)
@ConditionalOnProperty(prefix = TenantProperties.PRE + ".server", name = "enable", havingValue = "true")
public class TenantServerSyncPublisher {

    /**
     * 租户同步主题
     */
    public static final String TOPIC_TENANT_UPDATE = "tenant/update";
    public static final String TOPIC_TENANT_DELETE = "tenant/delete";
    public static final String TOPIC_TENANT_SERVICE = "tenant/service";

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired(required = false)
    private SyncServer syncServer;

    /**
     * 发布租户更新
     *
     * @param tenant 租户信息
     */
    public void publishTenantUpdate(SysTenant tenant) {
        if (!canPublish()) {
            return;
        }

        try {
            Map<String, Object> data = convertTenantToMap(tenant);
            syncServer.broadcast(TOPIC_TENANT_UPDATE, data);
            log.info("[租户服务端] 发布租户更新: {}", tenant.getSysTenantCode());
        } catch (Exception e) {
            log.error("[租户服务端] 发布租户更新失败", e);
        }
    }

    /**
     * 发布租户删除
     *
     * @param tenantId 租户ID
     */
    public void publishTenantDelete(Integer tenantId) {
        if (!canPublish()) {
            return;
        }

        try {
            Map<String, Object> data = Map.of("sysTenantId", tenantId);
            syncServer.broadcast(TOPIC_TENANT_DELETE, data);
            log.info("[租户服务端] 发布租户删除: {}", tenantId);
        } catch (Exception e) {
            log.error("[租户服务端] 发布租户删除失败", e);
        }
    }

    /**
     * 发布服务/菜单更新
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID列表
     */
    public void publishServiceUpdate(Integer tenantId, List<Integer> menuIds) {
        if (!canPublish()) {
            return;
        }

        try {
            Map<String, Object> data = Map.of(
                    "tenantId", tenantId,
                    "menuIds", menuIds
            );
            syncServer.broadcast(TOPIC_TENANT_SERVICE, data);
            log.info("[租户服务端] 发布服务更新: 租户={}, 菜单数={}", tenantId, menuIds.size());
        } catch (Exception e) {
            log.error("[租户服务端] 发布服务更新失败", e);
        }
    }

    /**
     * 检查是否可以发布
     */
    private boolean canPublish() {
        if (syncServer == null) {
            log.debug("[租户服务端] SyncServer 未注入，无法发布消息");
            return false;
        }

        if (!tenantProperties.isServerMode()) {
            return false;
        }

        if (!tenantProperties.getServer().isSyncEnable()) {
            return false;
        }

        return true;
    }

    /**
     * 转换租户为 Map
     */
    private Map<String, Object> convertTenantToMap(SysTenant tenant) {
        Map<String, Object> map = new HashMap<>();
        map.put("sysTenantId", tenant.getSysTenantId());
        map.put("sysTenantCode", tenant.getSysTenantCode());
        map.put("sysTenantName", tenant.getSysTenantName());
        map.put("sysTenantUsername", tenant.getSysTenantUsername());
        map.put("sysTenantPassword", tenant.getSysTenantPassword());
        map.put("sysTenantPhone", tenant.getSysTenantPhone());
        map.put("sysTenantEmail", tenant.getSysTenantEmail());
        map.put("sysTenantStatus", tenant.getSysTenantStatus());
        map.put("sysTenantDelete", tenant.getSysTenantDelete());
        return map;
    }
}

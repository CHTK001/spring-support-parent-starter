package com.chua.tenant.support.client.sync;

import com.chua.common.support.annotations.Spi;
import com.chua.sync.support.spi.SyncMessageHandler;
import com.chua.tenant.support.common.entity.SysTenant;
import com.chua.tenant.support.common.properties.TenantProperties;
import com.chua.tenant.support.server.mapper.SysTenantMapper;
import com.chua.tenant.support.server.mapper.SysTenantServiceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 租户客户端同步处理器
 * <p>
 * 实现 SyncMessageHandler 接口，监听服务端推送的租户数据并更新本地
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Component
@Spi("tenant")
@ConditionalOnClass(SyncMessageHandler.class)
@ConditionalOnBean(SysTenantMapper.class)
@ConditionalOnProperty(prefix = TenantProperties.PRE + ".client", name = "enable", havingValue = "true")
public class TenantClientSyncHandler implements SyncMessageHandler {

    /**
     * 租户同步主题
     */
    public static final String TOPIC_TENANT = "tenant";
    public static final String TOPIC_TENANT_UPDATE = "tenant/update";
    public static final String TOPIC_TENANT_DELETE = "tenant/delete";
    public static final String TOPIC_TENANT_SERVICE = "tenant/service";

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired(required = false)
    private SysTenantMapper sysTenantMapper;

    @Autowired(required = false)
    private SysTenantServiceMapper sysTenantServiceMapper;

    @Override
    public String getName() {
        return "tenant-client";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean supports(String topic) {
        return topic != null && topic.startsWith("tenant");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        // 客户端模式才处理
        if (!tenantProperties.isClientMode()) {
            return null;
        }

        // 检查是否启用同步
        if (!tenantProperties.getClient().isSyncEnable()) {
            return null;
        }

        log.debug("[租户客户端] 收到消息: topic={}", topic);

        return switch (topic) {
            case TOPIC_TENANT_UPDATE -> handleTenantUpdate(data);
            case TOPIC_TENANT_DELETE -> handleTenantDelete(data);
            case TOPIC_TENANT_SERVICE -> handleServiceUpdate(data);
            default -> null;
        };
    }

    /**
     * 处理租户更新
     */
    private Object handleTenantUpdate(Map<String, Object> data) {
        if (sysTenantMapper == null) {
            log.warn("[租户客户端] SysTenantMapper 未注入，无法处理租户更新");
            return null;
        }

        try {
            SysTenant tenant = convertToTenant(data);
            if (tenant == null || tenant.getSysTenantId() == null) {
                log.warn("[租户客户端] 租户数据不完整");
                return null;
            }

            // 检查是否存在
            boolean exists = sysTenantMapper.exists(
                    Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getSysTenantId, tenant.getSysTenantId())
            );

            if (exists) {
                // 更新
                sysTenantMapper.updateById(tenant);
                log.info("[租户客户端] 租户 {} 更新成功", tenant.getSysTenantCode());
            } else {
                // 新增
                sysTenantMapper.insert(tenant);
                log.info("[租户客户端] 租户 {} 新增成功", tenant.getSysTenantCode());
            }

            return Map.of("success", true);
        } catch (Exception e) {
            log.error("[租户客户端] 处理租户更新失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 处理租户删除
     */
    private Object handleTenantDelete(Map<String, Object> data) {
        if (sysTenantMapper == null) {
            return null;
        }

        try {
            Integer tenantId = (Integer) data.get("sysTenantId");
            if (tenantId == null) {
                return null;
            }

            sysTenantMapper.deleteById(tenantId);
            log.info("[租户客户端] 租户 {} 删除成功", tenantId);
            return Map.of("success", true);
        } catch (Exception e) {
            log.error("[租户客户端] 处理租户删除失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 处理服务/菜单更新
     */
    @SuppressWarnings("unchecked")
    private Object handleServiceUpdate(Map<String, Object> data) {
        try {
            Integer tenantId = (Integer) data.get("tenantId");
            List<Integer> menuIds = (List<Integer>) data.get("menuIds");

            if (tenantId == null) {
                return null;
            }

            log.info("[租户客户端] 租户 {} 服务更新，菜单数: {}", tenantId, menuIds != null ? menuIds.size() : 0);
            // 具体的菜单处理逻辑由上层应用实现
            return Map.of("success", true);
        } catch (Exception e) {
            log.error("[租户客户端] 处理服务更新失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 转换为租户对象
     */
    private SysTenant convertToTenant(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        SysTenant tenant = new SysTenant();
        tenant.setSysTenantId((Integer) data.get("sysTenantId"));
        tenant.setSysTenantCode((String) data.get("sysTenantCode"));
        tenant.setSysTenantName((String) data.get("sysTenantName"));
        tenant.setSysTenantUsername((String) data.get("sysTenantUsername"));
        tenant.setSysTenantPassword((String) data.get("sysTenantPassword"));
        tenant.setSysTenantPhone((String) data.get("sysTenantPhone"));
        tenant.setSysTenantEmail((String) data.get("sysTenantEmail"));
        tenant.setSysTenantStatus((Integer) data.get("sysTenantStatus"));
        tenant.setSysTenantDelete((Integer) data.get("sysTenantDelete"));
        return tenant;
    }
}

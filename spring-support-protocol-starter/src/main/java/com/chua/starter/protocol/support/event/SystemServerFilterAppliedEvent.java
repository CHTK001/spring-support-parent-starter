package com.chua.starter.protocol.support.event;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 过滤器安装/启用并已热应用后的事件回调。
 * <p>
 * 业务侧可监听该事件，将 event 中的 setting/configSnapshot 转换为业务数据库实体。
 *
 * @author CH
 */
public class SystemServerFilterAppliedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Action {
        INSTALL,
        ENABLE,
        UPDATE_CONFIG
    }

    private final Action action;
    private final Integer serverId;
    private final Integer settingId;
    private final String filterType;
    private final String filterId;
    private final Integer order;
    private final Boolean enabled;
    private final Map<String, Object> configSnapshot;

    public SystemServerFilterAppliedEvent(Action action,
                                         Integer serverId,
                                         Integer settingId,
                                         String filterType,
                                         String filterId,
                                         Integer order,
                                         Boolean enabled,
                                         Map<String, Object> configSnapshot) {
        this.action = action;
        this.serverId = serverId;
        this.settingId = settingId;
        this.filterType = filterType;
        this.filterId = filterId;
        this.order = order;
        this.enabled = enabled;
        this.configSnapshot = configSnapshot;
    }

    public Action getAction() {
        return action;
    }

    public Integer getServerId() {
        return serverId;
    }

    public Integer getSettingId() {
        return settingId;
    }

    public String getFilterType() {
        return filterType;
    }

    public String getFilterId() {
        return filterId;
    }

    public Integer getOrder() {
        return order;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Map<String, Object> getConfigSnapshot() {
        return configSnapshot;
    }
}


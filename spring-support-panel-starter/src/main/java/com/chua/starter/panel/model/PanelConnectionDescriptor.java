package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面板连接描述。
 */
@Data
@Builder
public class PanelConnectionDescriptor {

    private String connectionId;
    private String connectionName;
    private PanelConnectionType connectionType;
    private boolean cached;
    private boolean enabled;
    private LocalDateTime lastAccessTime;
}

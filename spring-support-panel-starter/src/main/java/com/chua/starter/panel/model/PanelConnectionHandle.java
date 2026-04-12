package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面板连接句柄。
 */
@Data
@Builder
public class PanelConnectionHandle {

    private String connectionId;
    private PanelConnectionDefinition definition;
    private Object nativeConnection;
    private LocalDateTime createdTime;
    private LocalDateTime lastAccessTime;
    private LocalDateTime expireTime;
}

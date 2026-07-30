package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirewallStatus {
    private Boolean enabled;
    private Integer activeRules;
    private String defaultInputPolicy;
    private String defaultOutputPolicy;
    private String defaultForwardPolicy;
    private String firewallType;
}

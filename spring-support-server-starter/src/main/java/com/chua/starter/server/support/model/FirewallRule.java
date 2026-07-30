package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirewallRule {
    private Integer id;
    private String name;
    private String direction;
    private String action;
    private String protocol;
    private String port;
    private String sourceIp;
    private String targetIp;
    private Boolean enabled;
    private String description;
    private Integer ruleIndex;
}

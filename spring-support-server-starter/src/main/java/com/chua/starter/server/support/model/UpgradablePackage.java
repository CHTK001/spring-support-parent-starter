package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradablePackage {
    private String name;
    private String installedVersion;
    private String latestVersion;
    private String source;
    private String architecture;
    private String description;
}

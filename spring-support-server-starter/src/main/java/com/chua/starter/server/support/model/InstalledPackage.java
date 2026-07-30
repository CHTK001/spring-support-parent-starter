package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstalledPackage {
    private String name;
    private String version;
    private String source;
    private String architecture;
    private String size;
    private String description;
    private String installDate;
}

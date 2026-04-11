package com.chua.starter.soft.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftRenderedConfigFile {
    private String templateCode;
    private String templateName;
    private String templatePath;
    private String content;
}

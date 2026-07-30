package com.chua.starter.project.pojo.setting;

import lombok.Data;

/**
 * SMTP 配置（project-starter 内部定义，避免依赖 api-service 模块）
 */
@Data
public class SmtpSetting {
    private String host;
    private Integer port;
    private String from;
    private String password;
}

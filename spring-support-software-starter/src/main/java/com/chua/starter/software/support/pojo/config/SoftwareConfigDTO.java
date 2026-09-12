package com.chua.starter.software.support.pojo.config;

import com.chua.starter.software.support.entity.SoftConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件配置响应
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "软件配置响应")
public class SoftwareConfigDTO extends SoftConfig {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;
}
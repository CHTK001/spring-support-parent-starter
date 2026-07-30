package com.chua.starter.software.support.pojo.config;

import com.chua.starter.software.support.entity.SoftConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件配置响应
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "软件配置响应")
public class SoftwareConfigDTO extends SoftConfig {

    private static final long serialVersionUID = 1L;
}
package com.chua.starter.software.support.pojo.config;

import com.chua.starter.software.support.entity.SoftConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件配置分页查询请求
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "软件配置分页查询请求")
public class SoftwareConfigPageRequest extends SoftConfig {

    private static final long serialVersionUID = 1L;

    /**
     * 配置分组（用于分组过滤）
     */
    @Schema(description = "配置分组")
    private String configGroup;
}
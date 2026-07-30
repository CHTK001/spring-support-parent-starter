package com.chua.starter.software.support.pojo.source;

import com.chua.starter.mybatis.entity.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件源分页查询请求
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "软件源分页查询请求")
public class SoftwareSourcePageRequest extends Query {

    /**
     * 源编码
     */
    @Schema(description = "源编码")
    private String sourceCode;

    /**
     * 源名称
     */
    @Schema(description = "源名称")
    private String sourceName;

    /**
     * 源类型: 0官方 1第三方 2自建 3本地
     */
    @Schema(description = "源类型: 0官方 1第三方 2自建 3本地")
    private Integer sourceType;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean sourceIsEnabled;
}
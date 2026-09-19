package com.chua.starter.software.support.pojo.source;

import com.chua.starter.mybatis.entity.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件源分页查询请求
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "软件源分页查询请求")
public class SoftwareSourcePageRequest extends Query {

    /**
     * 源编码
     */
    @Schema(description = "源编码")
    /** 来源代码 */
    private String sourceCode;

    /**
    * 源名称
    */
    @Schema(description = "源名称")
    /** 来源名称 */
    private String sourceName;

    /**
    * 源类型: 0官方 1第三方 2自建 3本地
    */
    @Schema(description = "源类型: 0官方 1第三方 2自建 3本地")
    /** 来源类型 */
    private Integer sourceType;

    /**
    * 是否启用
    */
    @Schema(description = "是否启用")
    /** 来源是否是否启用 */
    private Boolean sourceIsEnabled;
}

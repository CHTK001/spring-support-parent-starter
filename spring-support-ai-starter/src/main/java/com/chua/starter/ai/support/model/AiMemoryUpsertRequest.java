package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆写入/更新入参。
 *
 * <p>归属用户由服务端按当前登录用户写入，入参不接受 {@code userId}；
 * 更新时仅覆盖非 {@code null} 字段。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆写入请求")
public class AiMemoryUpsertRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "作用域：global/conversation，缺省 global")
    private String scope;

    @Schema(description = "所属会话标识（scope=conversation 时必填）")
    private String conversationId;

    @Schema(description = "种类：preference/fact/instruction/episode")
    private String kind;

    @Schema(description = "摘要标题")
    private String title;

    @Schema(description = "记忆内容，必填")
    private String content;

    @Schema(description = "检索关键词")
    private List<String> keywords;

    @Schema(description = "重要度 0-1，缺省按种类给默认值")
    private Double importance;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "状态：active/archived/expired，仅更新出口生效")
    private String status;

    @Schema(description = "有效天数，空或 0 表示永不过期")
    private Integer ttlDays;
}

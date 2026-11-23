package com.chua.starter.account.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个待导入模型（草稿）。
 *
 * <p>与前端 {@code ImportModelDraft} 同构；{@code task} 取值为
 * {@code chat / image_generation / video_generation}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChannelImportModelDraft {

    /** 模型 id（上游调用名） */
    @JsonProperty("id")
    private String id = "";

    /** 展示名（缺省同 id） */
    @JsonProperty("name")
    private String name = "";

    /** 上下文窗口（token），未知为 null */
    @JsonProperty("contextWindow")
    private Long contextWindow;

    /** 最大输出（token），未知为 null */
    @JsonProperty("maxOutputTokens")
    private Long maxOutputTokens;

    /** 能力任务类型 */
    @JsonProperty("task")
    private String task = "chat";
}

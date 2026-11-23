package com.chua.starter.account.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个待导入供应商（草稿）。
 *
 * <p>与前端 {@code ImportEntry} 同构：{@code skip} 非空表示该条不可导入
 * （仅预览展示原因）；{@code baseUrl} 为空同样视为不可导入。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChannelImportEntry {

    /** 来源内唯一键（如 {@code opencode:<id>}、{@code cc-switch:<app>:<id>}），勾选用 */
    @JsonProperty("key")
    private String key = "";

    /** 来源标识（与 SPI 扩展名一致：opencode / cc-switch / claude-settings / env / magpie / cursor / gemini-cli / codex-cli） */
    @JsonProperty("source")
    private String source = "";

    /** 建议供应商名（同批重名时已追加序号后缀） */
    @JsonProperty("name")
    private String name = "";

    /** 平台：custom（OpenAI 兼容）/ anthropic（Anthropic Messages） */
    @JsonProperty("platform")
    private String platform = "custom";

    /** 上游地址；空串 = 不可导入 */
    @JsonProperty("baseUrl")
    private String baseUrl = "";

    /** API Key；空串 = 无密钥（auth_scheme 落 none，导入后需补填） */
    @JsonProperty("apiKey")
    private String apiKey = "";

    /** 是否带密钥 */
    @JsonProperty("hasKey")
    private boolean hasKey;

    /** 模型清单（可为空：供应商先建，模型稍后手动添加） */
    @JsonProperty("models")
    private List<ChannelImportModelDraft> models = new ArrayList<>();

    /** 非空 = 跳过原因（该条不可导入，仅预览展示） */
    @JsonProperty("skip")
    private String skip;
}

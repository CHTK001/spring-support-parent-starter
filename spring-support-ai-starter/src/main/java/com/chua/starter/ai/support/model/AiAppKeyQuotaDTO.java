package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AppKey 额度调整请求：改上限 / 清零已用。
 *
 * <p>「按时间」「按次数」「按余额（总量）」三种配额在 appKey 上分别落到
 * {@code expire_datetime} / {@code max_use+use_count} / {@code max_tokens+total_tokens}，
 * 台账上的「续量」「重置」都收敛到这一个入口，避免为每种配额各开一个接口：</p>
 * <ul>
 *   <li>{@code maxUse}：次数上限。{@code 0} = 不限（置空）；{@code null} = 不改动。</li>
 *   <li>{@code maxTokens}：Token 总量上限。语义同上。</li>
 *   <li>{@code resetUsed}：为 true 时把已用次数与已耗 Token 清零（续期/续量后重新起算）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "AppKey 额度调整")
public class AiAppKeyQuotaDTO implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 次数上限：0 不限，null 不改动
     */
    @Schema(description = "次数上限（0=不限，null=不改动）")
    private Long maxUse;

    /**
     * Token 总量上限：0 不限，null 不改动
     */
    @Schema(description = "Token 总量上限（0=不限，null=不改动）")
    private Long maxTokens;

    /**
     * 是否清零已用（次数 / Token）
     */
    @Schema(description = "是否清零已用次数与 Token")
    private Boolean resetUsed;
}

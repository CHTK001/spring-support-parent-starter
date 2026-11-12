package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 采集平台运行参数项（界面渲染用）。
 *
 * <p>类型只有 boolean / number / text / select 四种，前端按类型选控件；
 * {@code restartRequired=true} 表示改完必须重启进程才生效（启动期才读取的开关）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "采集平台运行参数项")
public class CollectSettingVO {

    @Schema(description = "配置键")
    private String key;

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "分组：执行 / 抓取 / 安全 / 能力开关 / AI")
    private String group;

    @Schema(description = "控件类型：boolean / number / text / select")
    private String type;

    @Schema(description = "当前值（字符串，前端按类型转换）")
    private String value;

    @Schema(description = "yml 缺省值，用于「恢复默认」与差异提示")
    private String defaultValue;

    @Schema(description = "是否被界面覆盖过")
    private boolean overridden;

    @Schema(description = "是否重启后生效")
    private boolean restartRequired;

    @Schema(description = "是否只读（部署级配置，只能改 yml）")
    private boolean readOnly;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "数值下限，可空")
    private String min;

    @Schema(description = "数值上限，可空")
    private String max;

    @Schema(description = "单位，可空")
    private String unit;

    @Schema(description = "select 的候选项，可空")
    private List<String> options;
}

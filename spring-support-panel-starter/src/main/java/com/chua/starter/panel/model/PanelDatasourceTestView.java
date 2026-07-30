package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 数据源测试结果。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据源测试结果")
public class PanelDatasourceTestView {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean panelSuccess;
    /** 延迟（毫秒）。 */
    @Schema(description = "延迟（毫秒）")
    private Long panelLatencyMs;
    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String panelMessage;
    /** 探测版本。 */
    @Schema(description = "探测版本")
    private String panelDetectedVersion;
    /** 解析后的协议。 */
    @Schema(description = "解析后的协议")
    private String panelResolvedProtocol;
    /** 扩展结果。 */
    @Schema(description = "扩展结果")
    private Map<String, Object> panelDetails;
}

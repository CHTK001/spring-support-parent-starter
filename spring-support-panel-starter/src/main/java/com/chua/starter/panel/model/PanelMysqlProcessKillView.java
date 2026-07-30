package com.chua.starter.panel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MySQL 进程关闭结果。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "MySQL 进程关闭结果")
public class PanelMysqlProcessKillView {

    /** 成功关闭的进程 ID。 */
    @Schema(description = "成功关闭的进程 ID")
    private List<Long> killed;

    /** 失败的进程 ID 及原因。 */
    @Schema(description = "失败的进程 ID 及原因")
    private Map<Long, String> failed;
}


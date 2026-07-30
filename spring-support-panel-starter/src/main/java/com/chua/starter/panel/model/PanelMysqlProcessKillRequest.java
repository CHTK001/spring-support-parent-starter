package com.chua.starter.panel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * MySQL 进程关闭请求。
 *
 * @author CH
 */
@Data
@Schema(description = "MySQL 进程关闭请求")
public class PanelMysqlProcessKillRequest {

    /** 进程 ID 列表，对应 processlist 中的 Id。 */
    @Schema(description = "进程 ID 列表，对应 processlist 中的 Id")
    private List<Long> processIds;
}


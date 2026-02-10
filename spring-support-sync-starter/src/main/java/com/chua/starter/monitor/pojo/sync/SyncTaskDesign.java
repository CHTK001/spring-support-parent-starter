package com.chua.starter.sync.pojo.sync;

import com.chua.starter.sync.entity.MonitorSyncConnection;
import com.chua.starter.sync.entity.MonitorSyncNode;
import com.chua.starter.sync.entity.MonitorSyncTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 同步任务设计数据
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "同步任务设计数据")
public class SyncTaskDesign {

    /**
     * 任务基本信息
     */
    @Schema(description = "任务基本信息")
    private MonitorSyncTask task;

    /**
     * 节点列表
     */
    @Schema(description = "节点列表")
    private List<MonitorSyncNode> nodes;

    /**
     * 连线列表
     */
    @Schema(description = "连线列表")
    private List<MonitorSyncConnection> connections;

    /**
     * 前端布局数据(viewport等)
     */
    @Schema(description = "前端布局数据")
    private String layout;
}

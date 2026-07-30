package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.MongoPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MongoDB 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/mongo")
@Tag(name = "Panel Mongo", description = "Panel MongoDB 管理接口")
public class PanelMongoController {

    private final MongoPanelService mongoPanelService;

    /**
     * 打开 MongoDB 连接。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @Operation(summary = "打开连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 27017)));
        String username = body.containsKey("username") ? String.valueOf(body.get("username")) : null;
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String authDatabase = body.containsKey("authDatabase") ? String.valueOf(body.get("authDatabase")) : null;
        try {
            return ReturnResult.ok(mongoPanelService.open(host, port, username, password, authDatabase));
        } catch (Exception e) {
            return ReturnResult.error("MongoDB 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        mongoPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 列出数据库。
     *
     * @param connectionId 连接标识
     * @return 数据库列表
     */
    @Operation(summary = "列出数据库")
    @GetMapping("/{connectionId}/databases")
    public ReturnResult<List<String>> listDatabases(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(mongoPanelService.listDatabases(connectionId));
    }

    /**
     * 列出集合。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 集合列表
     */
    @Operation(summary = "列出集合")
    @GetMapping("/{connectionId}/collections")
    public ReturnResult<List<String>> listCollections(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database) {
        return ReturnResult.ok(mongoPanelService.listCollections(connectionId, database));
    }

    /**
     * 查询文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filter 过滤条件
     * @param sort 排序条件
     * @param limit 每页条数
     * @param skip 偏移量
     * @return 文档列表
     */
    @Operation(summary = "查询文档")
    @GetMapping("/{connectionId}/find")
    public ReturnResult<List<Map<String, Object>>> find(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @Parameter(description = "过滤条件") @RequestParam(defaultValue = "{}") String filter,
            @Parameter(description = "排序条件") @RequestParam(defaultValue = "{}") String sort,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int skip) {
        return ReturnResult.ok(mongoPanelService.find(connectionId, database, collection, filter, sort, limit, skip));
    }

    /**
     * 插入单条文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param documentJson 文档 JSON
     * @return 插入结果
     */
    @Operation(summary = "插入文档")
    @PostMapping("/{connectionId}/insert")
    public ReturnResult<Map<String, Object>> insertOne(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @RequestBody String documentJson) {
        return ReturnResult.ok(mongoPanelService.insertOne(connectionId, database, collection, documentJson));
    }

    /**
     * 更新单条文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filter 过滤条件
     * @param updateJson 更新 JSON
     * @return 影响条数
     */
    @Operation(summary = "更新文档")
    @PutMapping("/{connectionId}/update")
    public ReturnResult<Long> updateOne(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @Parameter(description = "过滤条件") @RequestParam String filter,
            @RequestBody String updateJson) {
        return ReturnResult.ok(mongoPanelService.updateOne(connectionId, database, collection, filter, updateJson));
    }

    /**
     * 删除单条文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filter 过滤条件
     * @return 删除条数
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{connectionId}/delete")
    public ReturnResult<Long> deleteOne(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @Parameter(description = "过滤条件") @RequestParam String filter) {
        return ReturnResult.ok(mongoPanelService.deleteOne(connectionId, database, collection, filter));
    }

    /**
     * 执行聚合查询。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param pipelineJson 聚合管道 JSON
     * @return 聚合结果
     */
    @Operation(summary = "聚合查询")
    @PostMapping("/{connectionId}/aggregate")
    public ReturnResult<List<Map<String, Object>>> aggregate(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @RequestBody String pipelineJson) {
        return ReturnResult.ok(mongoPanelService.aggregate(connectionId, database, collection, pipelineJson));
    }

    /**
     * 创建索引。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param unique 是否唯一索引
     * @param keysJson 索引键 JSON
     * @return 索引名称
     */
    @Operation(summary = "创建索引")
    @PostMapping("/{connectionId}/index")
    public ReturnResult<String> createIndex(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection,
            @Parameter(description = "是否唯一索引") @RequestParam(defaultValue = "false") boolean unique,
            @RequestBody String keysJson) {
        return ReturnResult.ok(mongoPanelService.createIndex(connectionId, database, collection, keysJson, unique));
    }

    /**
     * 删除集合。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @return 删除结果
     */
    @Operation(summary = "删除集合")
    @DeleteMapping("/{connectionId}/collection")
    public ReturnResult<Void> dropCollection(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection) {
        mongoPanelService.dropCollection(connectionId, database, collection);
        return ReturnResult.ok(null);
    }

    /**
     * 查询集合统计。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @return 统计信息
     */
    @Operation(summary = "集合统计")
    @GetMapping("/{connectionId}/stats")
    public ReturnResult<Map<String, Object>> stats(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @Parameter(description = "集合名称") @RequestParam String collection) {
        return ReturnResult.ok(mongoPanelService.stats(connectionId, database, collection));
    }

    /**
     * 查询数据库统计。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 统计信息
     */
    @Operation(summary = "数据库统计")
    @GetMapping("/{connectionId}/dbstats")
    public ReturnResult<Map<String, Object>> dbStats(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database) {
        return ReturnResult.ok(mongoPanelService.dbStats(connectionId, database));
    }

    /**
     * 执行 Mongo 原生命令。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param commandJson 命令 JSON
     * @return 执行结果
     */
    @Operation(summary = "执行命令")
    @PostMapping("/{connectionId}/command")
    public ReturnResult<Object> executeCommand(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam String database,
            @RequestBody String commandJson) {
        return ReturnResult.ok(mongoPanelService.executeCommand(connectionId, database, commandJson));
    }
}

package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.RedisHashFieldVO;
import com.chua.starter.monitor.support.model.RedisKeyDetailVO;
import com.chua.starter.monitor.support.model.RedisListElementVO;
import com.chua.starter.monitor.support.model.RedisManageCreateKeyDTO;
import com.chua.starter.monitor.support.model.RedisManageDatabaseVO;
import com.chua.starter.monitor.support.model.RedisManageScanResultVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;
import com.chua.starter.monitor.support.model.RedisValueVO;
import com.chua.starter.monitor.support.service.RedisManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据源级 Redis 管理 REST 接口。
 *
 * <p>操作对象为任意已登记的 Redis 数据源（type=redis），每次请求临时建连。
 * 与 {@code /ms/monitor/system/redis}（应用自身 Redis）相互独立。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "数据源级 Redis 管理")
@RestController
@RequestMapping("/ms/monitor/redis-manage")
@RequiredArgsConstructor
public class RedisManageController {

    /**
     * Redis 管理服务
     */
    private final RedisManageService redisManageService;

    /**
     * SCAN 键列表。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param pattern      键匹配模式
     * @param cursor       游标
     * @param count        每批数量
     * @return 键列表
     */
    @Operation(summary = "SCAN 键列表")
    @GetMapping("/keys")
    public ReturnResult<RedisManageScanResultVO> keys(@RequestParam Long datasourceId,
                                                      @RequestParam(defaultValue = "0") int db,
                                                      @RequestParam(defaultValue = "*") String pattern,
                                                      @RequestParam(defaultValue = "0") String cursor,
                                                      @RequestParam(defaultValue = "100") int count) {
        return call("扫描键", () -> redisManageService.scanKeys(datasourceId, db, pattern, cursor, count));
    }

    /**
     * 键详情。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @return 键详情
     */
    @Operation(summary = "键详情")
    @GetMapping("/key/{key}")
    public ReturnResult<RedisKeyDetailVO> keyDetail(@RequestParam Long datasourceId,
                                                    @RequestParam(defaultValue = "0") int db,
                                                    @PathVariable String key) {
        return call("读取键详情", () -> redisManageService.keyDetail(datasourceId, db, key));
    }

    /**
     * 新增键。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param dto          键定义
     * @return 是否成功
     */
    @Operation(summary = "新增键")
    @PostMapping("/key")
    public ReturnResult<Boolean> createKey(@RequestParam Long datasourceId,
                                           @RequestParam(defaultValue = "0") int db,
                                           @RequestBody RedisManageCreateKeyDTO dto) {
        return call("新增键", () -> redisManageService.createKey(datasourceId, db, dto));
    }

    /**
     * 更新 STRING 值。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param body         含 value
     * @return 是否成功
     */
    @Operation(summary = "更新 STRING 值")
    @PutMapping("/key/{key}")
    public ReturnResult<Boolean> setString(@RequestParam Long datasourceId,
                                          @RequestParam(defaultValue = "0") int db,
                                          @PathVariable String key,
                                          @RequestBody RedisValueVO body) {
        return call("保存 STRING", () -> redisManageService.setString(datasourceId, db, key, body.value()));
    }

    /**
     * 删除键（body: {keys: []}）。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param body         含 keys 数组
     * @return 删除数量
     */
    @Operation(summary = "删除键")
    @DeleteMapping("/key")
    public ReturnResult<Long> deleteKeys(@RequestParam Long datasourceId,
                                        @RequestParam(defaultValue = "0") int db,
                                        @RequestBody Map<String, List<String>> body) {
        List<String> keys = body == null ? List.of() : body.get("keys");
        return call("删除键", () -> redisManageService.deleteKeys(datasourceId, db, keys));
    }

    /**
     * 设置 TTL。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param ttl          过期秒数，-1 永久
     * @return 是否成功
     */
    @Operation(summary = "设置 TTL")
    @PostMapping("/key/{key}/expire")
    public ReturnResult<Boolean> expire(@RequestParam Long datasourceId,
                                       @RequestParam(defaultValue = "0") int db,
                                       @PathVariable String key,
                                       @RequestParam long ttl) {
        return call("设置 TTL", () -> redisManageService.expireKey(datasourceId, db, key, ttl));
    }

    /**
     * 重命名键。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          原键名
     * @param newKey       新键名
     * @return 是否成功
     */
    @Operation(summary = "重命名键")
    @PostMapping("/key/{key}/rename")
    public ReturnResult<Boolean> rename(@RequestParam Long datasourceId,
                                       @RequestParam(defaultValue = "0") int db,
                                       @PathVariable String key,
                                       @RequestParam String newKey) {
        return call("重命名键", () -> redisManageService.renameKey(datasourceId, db, key, newKey));
    }

    /**
     * Redis INFO。
     *
     * @param datasourceId 数据源 ID
     * @return 关键字段
     */
    @Operation(summary = "Redis INFO")
    @GetMapping("/info")
    public ReturnResult<Map<String, String>> info(@RequestParam Long datasourceId) {
        return call("读取 INFO", () -> redisManageService.info(datasourceId));
    }

    /**
     * 库列表及键数。
     *
     * @param datasourceId 数据源 ID
     * @return 各库键统计
     */
    @Operation(summary = "库列表及键数")
    @GetMapping("/databases")
    public ReturnResult<List<RedisManageDatabaseVO>> databases(@RequestParam Long datasourceId) {
        return call("读取库列表", () -> redisManageService.databases(datasourceId));
    }

    /**
     * HSET 写入哈希字段。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param field        字段
     * @return 是否新增
     */
    @Operation(summary = "写入哈希字段")
    @PostMapping("/key/{key}/hash")
    public ReturnResult<Boolean> hset(@RequestParam Long datasourceId,
                                     @RequestParam(defaultValue = "0") int db,
                                     @PathVariable String key,
                                     @RequestBody RedisHashFieldVO field) {
        return call("写入哈希字段", () -> redisManageService.hset(datasourceId, db, key, field.field(), field.value()));
    }

    /**
     * HDEL 删除哈希字段。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param field        字段名
     * @return 删除数量
     */
    @Operation(summary = "删除哈希字段")
    @DeleteMapping("/key/{key}/hash")
    public ReturnResult<Long> hdel(@RequestParam Long datasourceId,
                                  @RequestParam(defaultValue = "0") int db,
                                  @PathVariable String key,
                                  @RequestParam String field) {
        return call("删除哈希字段", () -> redisManageService.hdel(datasourceId, db, key, field));
    }

    /**
     * RPUSH 追加列表元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param element      元素
     * @return 列表长度
     */
    @Operation(summary = "追加列表元素")
    @PostMapping("/key/{key}/list")
    public ReturnResult<Long> rpush(@RequestParam Long datasourceId,
                                   @RequestParam(defaultValue = "0") int db,
                                   @PathVariable String key,
                                   @RequestBody RedisValueVO element) {
        return call("追加列表元素", () -> redisManageService.rpush(datasourceId, db, key, element.value()));
    }

    /**
     * LSET 覆盖列表元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param element      含下标与值
     * @return 是否成功
     */
    @Operation(summary = "覆盖列表元素")
    @PutMapping("/key/{key}/list")
    public ReturnResult<Boolean> lset(@RequestParam Long datasourceId,
                                    @RequestParam(defaultValue = "0") int db,
                                    @PathVariable String key,
                                    @RequestBody RedisListElementVO element) {
        return call("覆盖列表元素", () -> redisManageService.lset(datasourceId, db, key, element.index(), element.value()));
    }

    /**
     * 按下标删除列表元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param index        下标
     * @return 删除数量
     */
    @Operation(summary = "删除列表元素")
    @DeleteMapping("/key/{key}/list")
    public ReturnResult<Long> lrem(@RequestParam Long datasourceId,
                                 @RequestParam(defaultValue = "0") int db,
                                 @PathVariable String key,
                                 @RequestParam long index) {
        return call("删除列表元素", () -> redisManageService.lremIndex(datasourceId, db, key, index));
    }

    /**
     * SADD 添加集合成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员
     * @return 是否新增
     */
    @Operation(summary = "添加集合成员")
    @PostMapping("/key/{key}/set")
    public ReturnResult<Boolean> sadd(@RequestParam Long datasourceId,
                                    @RequestParam(defaultValue = "0") int db,
                                    @PathVariable String key,
                                    @RequestBody RedisValueVO member) {
        return call("添加集合成员", () -> redisManageService.sadd(datasourceId, db, key, member.value()));
    }

    /**
     * SREM 删除集合成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员
     * @return 删除数量
     */
    @Operation(summary = "删除集合成员")
    @DeleteMapping("/key/{key}/set")
    public ReturnResult<Long> srem(@RequestParam Long datasourceId,
                                  @RequestParam(defaultValue = "0") int db,
                                  @PathVariable String key,
                                  @RequestParam String member) {
        return call("删除集合成员", () -> redisManageService.srem(datasourceId, db, key, member));
    }

    /**
     * ZADD 写入有序集合成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员与分值
     * @return 是否新增
     */
    @Operation(summary = "写入有序集合成员")
    @PostMapping("/key/{key}/zset")
    public ReturnResult<Boolean> zadd(@RequestParam Long datasourceId,
                                    @RequestParam(defaultValue = "0") int db,
                                    @PathVariable String key,
                                    @RequestBody RedisMemberScoreVO member) {
        return call("写入有序集合成员", () -> redisManageService.zadd(datasourceId, db, key, member.member(), member.score()));
    }

    /**
     * ZREM 删除有序集合成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员
     * @return 删除数量
     */
    @Operation(summary = "删除有序集合成员")
    @DeleteMapping("/key/{key}/zset")
    public ReturnResult<Long> zrem(@RequestParam Long datasourceId,
                                  @RequestParam(defaultValue = "0") int db,
                                  @PathVariable String key,
                                  @RequestParam String member) {
        return call("删除有序集合成员", () -> redisManageService.zrem(datasourceId, db, key, member));
    }

    /**
     * XADD 追加流消息。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param entry        消息（字段列表）
     * @return 新消息 ID
     */
    @Operation(summary = "追加流消息")
    @PostMapping("/key/{key}/stream")
    public ReturnResult<String> xadd(@RequestParam Long datasourceId,
                                   @RequestParam(defaultValue = "0") int db,
                                   @PathVariable String key,
                                   @RequestBody RedisStreamEntryVO entry) {
        return call("追加流消息", () -> redisManageService.xadd(datasourceId, db, key, entry.fields()));
    }

    /**
     * 统一异常包装。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[redis-manage] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}

package com.chua.starter.monitor.support.controller;

import com.chua.starter.monitor.support.model.RedisHashFieldVO;
import com.chua.starter.monitor.support.model.RedisListElementVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;
import com.chua.starter.monitor.support.model.RedisValueVO;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.SystemRedisKvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
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

/**
 * 当前系统 Redis 集合类型键的元素级 REST 接口
 *
 * <p>与 {@code /ms/monitor/system/redis/kv/{key}} 的 STRING 读写互补：STRING 整体覆盖，
 * HASH/LIST/SET/ZSET/STREAM 只能按元素增删改，否则一次整体写入会把键类型改坏。</p>
 *
 * @since 2026/9/20
 * @author CH
 */
@Tag(name = "监控管理", description = "当前系统 Redis 集合类型元素操作")
@RestController
@RequestMapping("/ms/monitor/system/redis/kv")
public class SystemRedisKvController {

    /**
     * 当前系统 Redis 元素级操作服务
     */
    private final SystemRedisKvService kvService;

    /**
     * 构造方法。
     *
     * @param kvService 元素级操作服务
     */
    public SystemRedisKvController(SystemRedisKvService kvService) {
        this.kvService = kvService;
    }

    /**
     * 写入哈希字段。
     *
     * @param key   键名
     * @param field 字段
     * @return 是否新增字段（更新已有字段时为 false）
     */
    @Operation(summary = "写入 Redis 哈希字段")
    @PostMapping("/{key}/hash")
    public ReturnResult<Boolean> putHash(@PathVariable String key, @RequestBody RedisHashFieldVO field) {
        return call(() -> kvService.putHash(key, field));
    }

    /**
     * 删除哈希字段。
     *
     * @param key   键名
     * @param field 字段名
     * @return 删除数量
     */
    @Operation(summary = "删除 Redis 哈希字段")
    @DeleteMapping("/{key}/hash")
    public ReturnResult<Long> removeHash(@PathVariable String key, @RequestParam String field) {
        return call(() -> kvService.removeHash(key, field));
    }

    /**
     * 追加列表元素。
     *
     * @param key     键名
     * @param element 元素
     * @return 追加后的列表长度
     */
    @Operation(summary = "追加 Redis 列表元素")
    @PostMapping("/{key}/list")
    public ReturnResult<Long> pushList(@PathVariable String key, @RequestBody RedisListElementVO element) {
        return call(() -> kvService.pushList(key, element.value()));
    }

    /**
     * 覆盖列表元素。
     *
     * @param key     键名
     * @param element 元素（含下标）
     * @return 是否成功
     */
    @Operation(summary = "覆盖 Redis 列表元素")
    @PutMapping("/{key}/list")
    public ReturnResult<Boolean> setList(@PathVariable String key, @RequestBody RedisListElementVO element) {
        return call(() -> kvService.setList(key, element.index(), element.value()));
    }

    /**
     * 按下标删除列表元素。
     *
     * @param key   键名
     * @param index 下标
     * @return 删除数量
     */
    @Operation(summary = "按下标删除 Redis 列表元素")
    @DeleteMapping("/{key}/list")
    public ReturnResult<Long> removeList(@PathVariable String key, @RequestParam int index) {
        return call(() -> kvService.removeList(key, index));
    }

    /**
     * 添加集合成员。
     *
     * @param key   键名
     * @param value 成员值
     * @return 是否新增成员
     */
    @Operation(summary = "添加 Redis 集合成员")
    @PostMapping("/{key}/set")
    public ReturnResult<Boolean> addSet(@PathVariable String key, @RequestBody RedisValueVO value) {
        return call(() -> kvService.addSet(key, value.value()));
    }

    /**
     * 删除集合成员。
     *
     * @param key   键名
     * @param value 成员值
     * @return 删除数量
     */
    @Operation(summary = "删除 Redis 集合成员")
    @DeleteMapping("/{key}/set")
    public ReturnResult<Long> removeSet(@PathVariable String key, @RequestParam String value) {
        return call(() -> kvService.removeSet(key, value));
    }

    /**
     * 写入有序集合成员。
     *
     * @param key    键名
     * @param member 成员与分值
     * @return 是否新增成员
     */
    @Operation(summary = "写入 Redis 有序集合成员")
    @PostMapping("/{key}/zset")
    public ReturnResult<Boolean> addZset(@PathVariable String key, @RequestBody RedisMemberScoreVO member) {
        return call(() -> kvService.addZset(key, member));
    }

    /**
     * 删除有序集合成员。
     *
     * @param key    键名
     * @param member 成员值
     * @return 删除数量
     */
    @Operation(summary = "删除 Redis 有序集合成员")
    @DeleteMapping("/{key}/zset")
    public ReturnResult<Long> removeZset(@PathVariable String key, @RequestParam String member) {
        return call(() -> kvService.removeZset(key, member));
    }

    /**
     * 追加流消息。
     *
     * @param key   键名
     * @param entry 消息（id 留空由 Redis 生成）
     * @return 新消息 ID
     */
    @Operation(summary = "追加 Redis 流消息")
    @PostMapping("/{key}/stream")
    public ReturnResult<String> appendStream(@PathVariable String key, @RequestBody RedisStreamEntryVO entry) {
        return call(() -> kvService.appendStream(key, entry));
    }

    /**
     * 读取流消息。
     *
     * @param key   键名
     * @param limit 最多返回条数，默认 100
     * @return 消息列表，按 ID 逆序
     */
    @Operation(summary = "读取 Redis 流消息")
    @GetMapping("/{key}/stream")
    public ReturnResult<List<RedisStreamEntryVO>> readStream(@PathVariable String key,
                                                            @RequestParam(defaultValue = "100") int limit) {
        return call(() -> kvService.readStream(key, limit));
    }

    /**
     * 执行元素操作，把异常统一成失败响应。
     *
     * @param action 操作
     * @param <T>    返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(java.util.function.Supplier<T> action) {
        try {
            return ReturnResult.ok(action.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (DataAccessException e) {
            Throwable root = e.getMostSpecificCause();
            String detail = root.getMessage() != null ? root.getMessage() : e.getMessage();
            return ReturnResult.error("Redis 命令执行失败: " + detail);
        }
    }
}

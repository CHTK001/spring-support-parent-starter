package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.model.RedisHashFieldVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;
import com.chua.starter.monitor.support.service.SystemRedisKvService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前系统 Redis 集合类型键的元素级读写实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SystemRedisKvServiceImpl implements SystemRedisKvService {

    /**
     * 应用自身连接的 Redis 模板
     */
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * 构造方法。
     *
     * @param redisTemplateProvider 应用 Redis 模板
     */
    public SystemRedisKvServiceImpl(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public boolean putHash(String key, RedisHashFieldVO field) {
        if (field == null || isBlank(field.field())) {
            throw new IllegalArgumentException("哈希字段名不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "HASH");
        boolean isNew = !Boolean.TRUE.equals(template.opsForHash().hasKey(key, field.field()));
        template.opsForHash().put(key, field.field(), nullToEmpty(field.value()));
        return isNew;
    }

    @Override
    public long removeHash(String key, String field) {
        if (isBlank(field)) {
            throw new IllegalArgumentException("哈希字段名不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "HASH");
        Long removed = template.opsForHash().delete(key, field);
        return removed == null ? 0L : removed;
    }

    @Override
    public long pushList(String key, String value) {
        StringRedisTemplate template = template();
        requireType(key, "LIST");
        Long size = template.opsForList().rightPush(key, nullToEmpty(value));
        return size == null ? 0L : size;
    }

    @Override
    public boolean setList(String key, int index, String value) {
        StringRedisTemplate template = template();
        requireType(key, "LIST");
        Long size = template.opsForList().size(key);
        if (size == null || index < 0 || index >= size) {
            throw new IllegalArgumentException("列表下标越界: " + index + "，当前长度 " + (size == null ? 0 : size));
        }
        template.opsForList().set(key, index, nullToEmpty(value));
        return true;
    }

    @Override
    public long removeList(String key, int index) {
        StringRedisTemplate template = template();
        requireType(key, "LIST");
        Long size = template.opsForList().size(key);
        if (size == null || index < 0 || index >= size) {
            throw new IllegalArgumentException("列表下标越界: " + index + "，当前长度 " + (size == null ? 0 : size));
        }
        String current = template.opsForList().index(key, index);
        Long removed = template.opsForList().remove(key, 1, current);
        return removed == null ? 0L : removed;
    }

    @Override
    public boolean addSet(String key, String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("集合成员不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "SET");
        Long added = template.opsForSet().add(key, value);
        return added != null && added > 0;
    }

    @Override
    public long removeSet(String key, String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("集合成员不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "SET");
        Long removed = template.opsForSet().remove(key, value);
        return removed == null ? 0L : removed;
    }

    @Override
    public boolean addZset(String key, RedisMemberScoreVO member) {
        if (member == null || isBlank(member.member())) {
            throw new IllegalArgumentException("有序集合成员不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "ZSET");
        Boolean added = template.opsForZSet().add(key, member.member(), member.score());
        return Boolean.TRUE.equals(added);
    }

    @Override
    public long removeZset(String key, String member) {
        if (isBlank(member)) {
            throw new IllegalArgumentException("有序集合成员不能为空");
        }
        StringRedisTemplate template = template();
        requireType(key, "ZSET");
        Long removed = template.opsForZSet().remove(key, member);
        return removed == null ? 0L : removed;
    }

    @Override
    public String appendStream(String key, RedisStreamEntryVO entry) {
        if (entry == null || entry.fields() == null || entry.fields().isEmpty()) {
            throw new IllegalArgumentException("流消息至少需要一个字段");
        }
        StringRedisTemplate template = template();
        requireType(key, "STREAM");
        Map<String, String> body = new LinkedHashMap<>();
        for (RedisHashFieldVO field : entry.fields()) {
            if (field == null || isBlank(field.field())) {
                throw new IllegalArgumentException("流消息字段名不能为空");
            }
            body.put(field.field(), nullToEmpty(field.value()));
        }
        var recordId = template.opsForStream().add(key, body);
        if (recordId == null) {
            throw new IllegalStateException("写入流消息失败");
        }
        return recordId.getValue();
    }

    @Override
    public List<RedisStreamEntryVO> readStream(String key, int limit) {
        StringRedisTemplate template = template();
        if (isAbsent(key)) {
            return List.of();
        }
        requireType(key, "STREAM");
        int max = Math.max(1, limit);
        List<RedisStreamEntryVO> result = new ArrayList<>();
        for (var record : template.opsForStream()
                .reverseRange(key, Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(max))) {
            List<RedisHashFieldVO> fields = new ArrayList<>();
            var body = record.getValue();
            if (body != null) {
                for (Map.Entry<Object, Object> item : body.entrySet()) {
                    fields.add(new RedisHashFieldVO(
                            String.valueOf(item.getKey()),
                            item.getValue() == null ? "" : String.valueOf(item.getValue())
                    ));
                }
            }
            result.add(new RedisStreamEntryVO(record.getId() == null ? "" : record.getId().getValue(), fields));
        }
        return result;
    }

    /**
     * 获取模板，未配置 Redis 时抛出可读异常。
     *
     * @return 应用 Redis 模板
     */
    private StringRedisTemplate template() {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("当前系统未配置 Redis");
        }
        return template;
    }

    /**
     * 键是否存在。
     *
     * @param key 键名
     * @return 是否存在
     */
    private boolean isAbsent(String key) {
        Boolean exists = template().hasKey(key);
        return !Boolean.TRUE.equals(exists);
    }

    /**
     * 校验键类型，不存在（首次写入）或类型不符时给出明确原因。
     *
     * @param key      键名
     * @param expected 期望类型
     */
    private void requireType(String key, String expected) {
        StringRedisTemplate template = template();
        var type = template.type(key);
        String actual = type == null ? "NONE" : type.name();
        if ("NONE".equals(actual)) {
            return;
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("键 " + key + " 当前类型是 " + actual + "，不是 " + expected);
        }
    }

    /**
     * 是否空白字符串。
     *
     * @param value 值
     * @return 是否空白
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * null 转空串。
     *
     * @param value 值
     * @return 非 null 值
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.chua.starter.lock.configuration;

import com.chua.common.support.task.layer.idempotent.IdempotentProvider;
import com.chua.common.support.task.layer.idempotent.RedisIdempotentProvider;
import com.chua.starter.lock.properties.LockProperties;
import com.chua.starter.lock.support.StoredResultCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Redis 幂等适配自动配置。
 *
 * @author CH
 * @since 2026-03-28
 */
@AutoConfiguration(afterName = "com.chua.starter.redis.support.RedisConfiguration")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = LockProperties.PRE, name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = LockProperties.PRE + ".idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${plugin.lock.idempotent.provider:auto}' == 'auto' or '${plugin.lock.idempotent.provider:auto}' == 'redis'")
public class LockRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentProvider.class)
    public IdempotentProvider redisIdempotentProvider(
            StringRedisTemplate stringRedisTemplate,
            StoredResultCodec storedResultCodec,
            LockProperties lockProperties) {
        return RedisIdempotentProvider.builder()
                .keyPrefix(lockProperties.getIdempotent().getKeyPrefix())
                .serializer(storedResultCodec::serialize)
                .deserializer(storedResultCodec::deserialize)
                .redisExecutor(createRedisExecutor(stringRedisTemplate))
                .build();
    }

    private BiFunction<String, String[], Object> createRedisExecutor(StringRedisTemplate stringRedisTemplate) {
        return (command, arguments) -> switch (command.toUpperCase(Locale.ROOT)) {
            case "EXISTS" -> Boolean.TRUE.equals(stringRedisTemplate.hasKey(arguments[0])) ? 1L : 0L;
            case "GET" -> stringRedisTemplate.opsForValue().get(arguments[0]);
            case "SETEX" -> {
                stringRedisTemplate.opsForValue().set(arguments[0], arguments[2], java.time.Duration.ofSeconds(Long.parseLong(arguments[1])));
                yield "OK";
            }
            case "DEL" -> {
                List<String> keys = Arrays.asList(arguments);
                Long deleted = stringRedisTemplate.delete(keys);
                yield deleted == null ? 0L : deleted;
            }
            case "EVAL" -> executeEval(stringRedisTemplate, arguments);
            default -> throw new IllegalArgumentException("Unsupported Redis command: " + command);
        };
    }

    private Long executeEval(StringRedisTemplate stringRedisTemplate, String[] arguments) {
        String scriptText = arguments[0];
        int keyCount = Integer.parseInt(arguments[1]);
        List<String> keys = Arrays.asList(Arrays.copyOfRange(arguments, 2, 2 + keyCount));
        Object[] scriptArguments = Arrays.copyOfRange(arguments, 2 + keyCount, arguments.length);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(scriptText);

        Long result = stringRedisTemplate.execute(script, keys, scriptArguments);
        return result == null ? 0L : result;
    }
}

package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.vo.HostFileDigestVO;
import com.chua.starter.server.support.service.HostFileDigestService;
import com.chua.starter.server.support.service.HostFileService;
import com.chua.starter.server.support.util.HostFilePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主机文件指纹台账实现。
 *
 * <p>口径：<b>服务端永远自己算一遍</b>，不信调用方给的字符串 —— 前端算 MD5 的意义是
 * 「传完之后确认服务端收到的是同一个字节流」，直接采信声明值等于把这个校验取消了。</p>
 *
 * <p>一致时记 {@code UPLOAD}（上传声明），不一致直接返回失败并说明两个值；
 * 列表页按需计算时记 {@code SERVER}，两种来源都进同一张表。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostFileDigestServiceImpl implements HostFileDigestService {

    /**
     * 来源：上传时声明
     */
    private static final String SOURCE_UPLOAD = "UPLOAD";

    /**
     * 主机文件服务（算 MD5 与取存储名）
     */
    private final HostFileService hostFileService;

    /**
     * 台账仓库
     */
    private final HostFileDigestRepository repository;

    @Override
    public ReturnResult<String> confirm(String bucket, String key, String expected) {
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            return ReturnResult.illegal("文件 key 不合法");
        }
        ReturnResult<HostFileDigestVO> computed = hostFileService.md5(bucket, normalized);
        if (!computed.isOk() || computed.getData() == null) {
            return ReturnResult.error(computed.getMsg());
        }
        HostFileDigestVO digest = computed.getData();
        String actual = digest.md5() == null ? "" : digest.md5().toLowerCase(Locale.ROOT);
        String declare = expected == null ? "" : expected.trim().toLowerCase(Locale.ROOT);
        if (!declare.isEmpty() && !declare.equals(actual)) {
            log.warn("[主机文件] MD5 不一致: key={} 声明={} 实际={}", normalized, declare, actual);
            return ReturnResult.error("MD5 校验失败：文件内容与声明的指纹不一致（声明 " + declare
                    + "，实际 " + actual + "），请重新上传");
        }
        repository.save(resolveBucket(bucket), normalized, actual, digest.size(), SOURCE_UPLOAD);
        return ReturnResult.ok(actual);
    }

    @Override
    public Map<String, String> lookup(String bucket, Iterable<String> keys) {
        List<String> normalized = new ArrayList<>();
        for (String key : keys) {
            String value = HostFilePaths.normalizeKey(key);
            if (value != null && !value.isEmpty()) {
                normalized.add(value);
            }
        }
        return repository.find(resolveBucket(bucket), normalized);
    }

    /**
     * 台账按存储提供者分区：同一个 key 换了存储桶就不是同一个文件。
     *
     * @param bucket 请求里的提供者名
     * @return 实际生效的提供者名（空串收敛到 unset，避免 null 主键）
     */
    private String resolveBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "_default" : bucket.trim();
    }
}

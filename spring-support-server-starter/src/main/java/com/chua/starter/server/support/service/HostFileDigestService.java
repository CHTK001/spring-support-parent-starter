package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.Map;

/**
 * 主机文件指纹台账：记录「哪个文件传上来时声明的 MD5 是多少、服务端复核出来的又是多少」。
 *
 * <p>为什么另建台账而不是只算一次：MD5 是事后追查的硬证据 —— 某个包是什么时候传的、
 * 和发布投放时用的是不是同一个字节流，只有落库才查得到。列表页也要直接显示它，
 * 不能每次进目录都把几百 MB 的包重算一遍。</p>
 *
 * <p><b>降级</b>：没有数据源时所有写入静默跳过，读回空表；上传与浏览绝不受影响。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
public interface HostFileDigestService {

    /**
     * 复核并登记：服务端按文件真实字节算一遍 MD5，与调用方声明的一致才落库。
     *
     * @param bucket   存储提供者名（留空为默认）
     * @param key      文件 key
     * @param expected 调用方声明的 MD5（小写 hex，可空：为空则只记录实际值）
     * @return 实际 MD5；不一致时返回失败并说明差异
     */
    ReturnResult<String> confirm(String bucket, String key, String expected);

    /**
     * 批量取已登记的 MD5。
     *
     * @param bucket 存储提供者名
     * @param keys   文件 key
     * @return key → md5；没有记录的 key 不出现
     */
    Map<String, String> lookup(String bucket, Iterable<String> keys);
}

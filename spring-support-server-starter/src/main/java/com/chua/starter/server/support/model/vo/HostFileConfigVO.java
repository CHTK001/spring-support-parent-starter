package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 主机文件存储信息。
 *
 * <p>「未启用」与「已启用但读不到根目录」是两回事：前者没装文件存储，
 * 后者是对象存储没有本地根路径（仍可浏览）。前端据此决定是否禁用上传 /
 * 是否展示主机绝对路径。</p>
 *
 * @param enabled       文件存储是否可用
 * @param bucket        当前使用的存储提供者名
 * @param root          本机存储根目录；非本地存储为空串
 * @param local         是否本地存储（可算 MD5、可重命名 / 建目录、有主机绝对路径）
 * @param buckets       可切换的存储提供者名
 * @param defaultBucket 默认提供者名
 * @author CH
 * @since 4.0.0.62
 */
public record HostFileConfigVO(
        boolean enabled,
        String bucket,
        String root,
        boolean local,
        List<String> buckets,
        String defaultBucket
) {

    /**
     * 规范构造器：桶列表做防御性拷贝。
     *
     * @param enabled       是否可用
     * @param bucket        当前提供者
     * @param root          本机根目录
     * @param local         是否本地存储
     * @param buckets       可切换提供者
     * @param defaultBucket 默认提供者
     */
    public HostFileConfigVO {
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }
}

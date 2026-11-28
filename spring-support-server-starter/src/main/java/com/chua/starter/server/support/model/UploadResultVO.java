package com.chua.starter.server.support.model;

/**
 * 主机文件上传结果视图：携带覆盖判定结论，前端据此提示「已上传 / 内容一致跳过 / 目标较新拒绝」。
 *
 * @param status                   {@link #UPLOADED} / {@link #SKIPPED_SAME} / {@link #TARGET_NEWER}
 * @param path                     远端目标路径
 * @param message                  面向用户的结论说明
 * @param sourceMd5                上传内容 MD5（小写十六进制；计算失败为 null）
 * @param targetMd5                目标文件 MD5（目标不存在或未取到为 null）
 * @param targetLastModifiedMillis 目标文件最后修改时间（epoch 毫秒；未取到为 null）
 * @author CH
 * @since 4.0.0.52
 */
public record UploadResultVO(
        String status,
        String path,
        String message,
        String sourceMd5,
        String targetMd5,
        Long targetLastModifiedMillis) {

    /**
     * 已上传（含强制覆盖）
     */
    public static final String UPLOADED = "UPLOADED";

    /**
     * 目标文件内容一致（md5 相同），已跳过上传
     */
    public static final String SKIPPED_SAME = "SKIPPED_SAME";

    /**
     * 目标文件更新时间较新，已拒绝覆盖（需 force 才能继续）
     */
    public static final String TARGET_NEWER = "TARGET_NEWER";
}

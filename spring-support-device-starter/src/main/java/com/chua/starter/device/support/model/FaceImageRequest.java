package com.chua.starter.device.support.model;

/**
 * 人脸图片请求体。
 *
 * <p>字段对应海康云眸官方 {@code basic/faces/update} 的 {@code faceImageBase64}，
 * 传入时不带 {@code data:image/...;base64,} 前缀。</p>
 *
 * @param imageBase64 人脸图片 base64
 * @since 4.0.0.51
 * @author CH
 */
public record FaceImageRequest(String imageBase64) {
}

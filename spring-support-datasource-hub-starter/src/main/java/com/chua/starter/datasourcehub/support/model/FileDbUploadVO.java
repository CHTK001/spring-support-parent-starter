package com.chua.starter.datasourcehub.support.model;

/**
 * 文件库（SQLite / DuckDB / H2）库文件上传结果。
 *
 * <p>只回三样东西：净化后的原名、大小、**服务器上的绝对路径** —— 前端把 path 直接
 * 填进「库文件路径」输入框即可，不需要知道落盘目录是怎么拼的。</p>
 *
 * @param name 原始文件名（净化后）
 * @param size 文件大小（字节）
 * @param path 服务器上的绝对路径
 * @author CH
 * @since 4.0.0.46
 */
public record FileDbUploadVO(String name, long size, String path) {
}
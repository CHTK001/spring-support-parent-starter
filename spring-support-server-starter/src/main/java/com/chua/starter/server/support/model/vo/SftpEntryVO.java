package com.chua.starter.server.support.model.vo;

/**
 * SFTP 目录条目
 * <p>
 * 远端目录浏览的单条结果。{@code directory} 与 {@code file} 同时给出，
 * 前端按其一判断图标与操作，不需自行取反。
 * </p>
 *
 * @param name      条目名称（相对所在目录）
 * @param path      远端绝对路径
 * @param directory 是否目录
 * @param file      是否非目录条目
 * @param size      字节数，底层未给出属性时为空
 * @author CH
 * @since 4.0.0.42
 */
public record SftpEntryVO(
        String name,
        String path,
        boolean directory,
        boolean file,
        Long size
) {
}

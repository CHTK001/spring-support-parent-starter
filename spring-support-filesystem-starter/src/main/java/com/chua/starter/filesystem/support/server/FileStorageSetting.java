package com.chua.starter.filesystem.support.server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件存储设置
 *
 * @since 2024/12/28
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStorageSetting {
    /** Openpreview */
    private boolean openPreview;
    /** Open下载 */
    private boolean openDownload;
    /** Openrange */
    private boolean openRange;
    /** Openwatermark */
    private boolean openWatermark;
    /** Openwebjars */
    private boolean openWebjars;
    /** Open远程文件 */
    private boolean openRemoteFile;
    /** Watermark */
    private String watermark;
}

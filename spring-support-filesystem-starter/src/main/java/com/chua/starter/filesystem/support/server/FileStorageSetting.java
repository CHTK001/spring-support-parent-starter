package com.chua.starter.filesystem.support.server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件存储设置
 *
 * @author CH
 * @since 2024/12/28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStorageSetting {
    private boolean openPreview;
    private boolean openDownload;
    private boolean openRange;
    private boolean openWatermark;
    private boolean openWebjars;
    private boolean openRemoteFile;
    private String watermark;
}

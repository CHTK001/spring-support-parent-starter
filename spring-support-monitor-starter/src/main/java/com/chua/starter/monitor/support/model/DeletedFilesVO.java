package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 已删除文件采集结果。
 *
 * <p>有的平台拿不到「被进程占用但已删除」的文件（典型是 Windows：需要管理员权限与
 * 额外的句柄枚举工具）。此时用 {@code supported=false} + {@code reason} 明确说明，
 * 不能返回一个空列表让用户以为「这台机器没有这种文件」。</p>
 *
 * @param supported 当前平台 / 通道是否支持该采集
 * @param reason    不支持时的原因说明
 * @param rows      采集到的行（不支持时为空）
 * @author CH
 * @since 4.0.0.51
 */
public record DeletedFilesVO(boolean supported, String reason, List<DeletedFileRowVO> rows) {

    /**
     * 支持且有数据。
     *
     * @param rows 行列表
     * @return 结果
     */
    public static DeletedFilesVO of(List<DeletedFileRowVO> rows) {
        return new DeletedFilesVO(true, "", rows == null ? List.of() : rows);
    }

    /**
     * 平台不支持。
     *
     * @param reason 原因
     * @return 结果
     */
    public static DeletedFilesVO unsupported(String reason) {
        return new DeletedFilesVO(false, reason, List.of());
    }
}

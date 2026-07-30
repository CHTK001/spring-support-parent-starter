package com.chua.starter.software.support.spi;

import java.io.File;

/**
 * 软件源操作结果
 *
 * <p>用于封装软件源操作（安装、卸载、更新）的返回结果。</p>
 *
 * @author CH
 * @since 2026/7/25
 */
public class SoftwareSourceResult {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 安装路径
     */
    private String installPath;

    /**
     * 下载文件
     */
    private File downloadedFile;

    public SoftwareSourceResult() {
    }

    public SoftwareSourceResult(boolean success, String message, String installPath, File downloadedFile) {
        this.success = success;
        this.message = message;
        this.installPath = installPath;
        this.downloadedFile = downloadedFile;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public File getDownloadedFile() {
        return downloadedFile;
    }

    public void setDownloadedFile(File downloadedFile) {
        this.downloadedFile = downloadedFile;
    }

    /**
     * 创建成功结果
     *
     * @param message 消息
     * @param installPath 安装路径
     * @return 成功结果
     */
    public static SoftwareSourceResult success(String message, String installPath) {
        return new SoftwareSourceResult(true, message, installPath, null);
    }

    /**
     * 创建成功结果
     *
     * @param message 消息
     * @param installPath 安装路径
     * @param downloadedFile 下载文件
     * @return 成功结果
     */
    public static SoftwareSourceResult success(String message, String installPath, File downloadedFile) {
        return new SoftwareSourceResult(true, message, installPath, downloadedFile);
    }

    /**
     * 创建失败结果
     *
     * @param message 错误消息
     * @return 失败结果
     */
    public static SoftwareSourceResult fail(String message) {
        return new SoftwareSourceResult(false, message, null, null);
    }
}
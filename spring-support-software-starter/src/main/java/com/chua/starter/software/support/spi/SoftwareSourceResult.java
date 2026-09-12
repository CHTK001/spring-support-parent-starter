package com.chua.starter.software.support.spi;

import java.io.File;

/**
 * 软件源操作结果
 *
 * <p>用于封装软件源操作（安装、卸载、更新）的返回结果。</p>
 *
 * @since 2026/7/25
 * @author CH
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

    /** 创建 software源结果 实例 */
    public SoftwareSourceResult() {
    }

    /**
      * 创建 software源结果 实例
     * @param success 成功
     * @param message 字符串
     * @param message 字符串
     * @param downloadedFile 文件
     * @param message 消息
     * @param installPath install路径
     * @param downloadedFile downloaded文件
     */
    public SoftwareSourceResult(boolean success, String message, String installPath, File downloadedFile) {
        this.success = success;
        this.message = message;
        this.installPath = installPath;
        this.downloadedFile = downloadedFile;
    }

    /**
     * 是否成功
     *
     * @return 是否成功的结果
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 设置成功
     *
     * @param success 成功
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 获取消息
     *
     * @return 获取消息的结果
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置消息
     *
     * @param message 消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取install路径
     *
     * @return 获取install路径的结果
     */
    public String getInstallPath() {
        return installPath;
    }

    /**
     * 设置install路径
     *
     * @param installPath install路径
     */
    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    /**
     * 获取downloaded文件
     *
     * @return 获取downloaded文件的结果
     */
    public File getDownloadedFile() {
        return downloadedFile;
    }

    /**
     * 设置downloaded文件
     *
     * @param downloadedFile downloaded文件
     */
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
        /**
         * software源结果。
         * @param true true
         * @param message 消息
         * @param installPath install路径
         * @param null 空
         */
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
        /**
         * software源结果。
         * @param true true
         * @param message 消息
         * @param installPath install路径
         * @param downloadedFile downloaded文件
         */
        return new SoftwareSourceResult(true, message, installPath, downloadedFile);
    }

    /**
     * 创建失败结果
     *
     * @param message 错误消息
     * @return 失败结果
     */
    public static SoftwareSourceResult fail(String message) {
        /**
         * software源结果。
         * @param false false
         * @param message 消息
         * @param null 空
         * @param null 空
         */
        return new SoftwareSourceResult(false, message, null, null);
    }
}
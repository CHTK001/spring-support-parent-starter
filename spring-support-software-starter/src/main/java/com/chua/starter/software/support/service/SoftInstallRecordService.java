package com.chua.starter.software.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.pojo.install.InstallDetailResponse;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 安装记录表 服务 接口
 *
 * @since 2026/7/25
 * @author CH
 */
public interface SoftInstallRecordService extends IService<SoftInstallRecord> {

    /**
     * 安装软件
     *
     * @param request 安装请求
     * @return 返回安装详情
     */
    ReturnResult<InstallDetailResponse> install(InstallRequest request);

    /**
     * 卸载软件
     *
     * @param softwareId 软件标识
     * @param userId 用户标识
     * @param deviceId 设备标识
     * @return 返回卸载详情
     */
    ReturnResult<InstallDetailResponse> uninstall(String softwareId, String userId, String deviceId);

    /**
     * 升级软件
     *
     * @param softwareId 软件标识
     * @param userId 用户标识
     * @param deviceId 设备标识
     * @param targetVersion 目标版本
     * @return 返回升级详情
     */
    ReturnResult<InstallDetailResponse> updateVersion(String softwareId, String userId, String deviceId, String targetVersion);

    /**
     * 根据软件标识获取安装记录
     *
     * @param softwareId 软件标识
     * @return 安装记录列表
     */
    List<SoftInstallRecord> listBySoftwareId(String softwareId);

    /**
     * 根据用户标识获取安装记录
     *
     * @param userId 用户标识
     * @return 安装记录列表
     */
    List<SoftInstallRecord> listByUserId(String userId);

    /**
     * 获取用户已安装的软件记录
     *
     * @param userId 用户标识
     * @return 已安装记录列表
     */
    List<SoftInstallRecord> listInstalledByUserId(String userId);

    /**
     * 获取已安装记录
     *
     * @param softwareId 软件标识
     * @param userId 用户标识
     * @param deviceId 设备标识
     * @return 安装记录
     */
    SoftInstallRecord getInstalledRecord(String softwareId, String userId, String deviceId);

    /**
     * 获取用户已安装的软件标识列表
     *
     * @param userId 用户标识
     * @return 已安装软件ID列表
     */
    List<String> getInstalledSoftwareIds(String userId);

    /**
     * 获取安装记录详情
     *
     * @param recordId 记录标识
     * @return 安装详情
     */
    ReturnResult<InstallDetailResponse> getInstallDetail(String recordId);

    /**
     * 更新安装进度
     *
     * @param recordId 记录标识
     * @param progress 进度
     * @param message 消息
     * @return 更新结果
     */
    ReturnResult<Boolean> updateProgress(String recordId, Integer progress, String message);

    /**
     * 完成安装
     *
     * @param recordId 记录标识
     * @return 结果
     */
    ReturnResult<Boolean> completeInstall(String recordId);

    /**
     * 安装失败
     *
     * @param recordId 记录标识
     * @param errorMessage 错误信息
     * @return 结果
     */
    ReturnResult<Boolean> failInstall(String recordId, String errorMessage);

    /**
     * 完成卸载
     *
     * @param recordId 记录标识
     * @return 结果
     */
    ReturnResult<Boolean> completeUninstall(String recordId);

    /**
     * 卸载失败
     *
     * @param recordId 记录标识
     * @param errorMessage 错误信息
     * @return 结果
     */
    ReturnResult<Boolean> failUninstall(String recordId, String errorMessage);
}

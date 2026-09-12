package com.chua.starter.software.support.service.local;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.pojo.install.InstallRequest;

import java.util.List;

/**
 * 本地软件服务接口
 *
 * <p>封装本地软件扫描、安装、卸载、管理等业务逻辑。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
public interface LocalSoftwareService {

    /**
     * 扫描本地软件
     *
     * <p>触发本地软件扫描并同步到数据库。</p>
     *
     * @param sourceId 软件源标识（可选，为空时使用默认本地源）
     * @return 扫描到的软件数量
     */
    int scanLocalSoftware(String sourceId);

    /**
     * 清空本地软件
     *
     * <p>将指定本地源下的所有软件标记为已删除状态。</p>
     *
     * @param sourceId 软件源标识
     * @return 是否成功
     */
    boolean clearLocalSoftware(String sourceId);

    /**
     * 安装本地软件
     *
     * @param request 安装请求
     * @return 安装结果
     */
    ReturnResult<Object> installLocalSoftware(InstallRequest request);

    /**
     * 卸载本地软件
     *
     * @param softwareId 软件标识
     * @param userId     用户标识
     * @param deviceId   设备标识（可选）
     * @return 卸载结果
     */
    ReturnResult<Object> uninstallLocalSoftware(String softwareId, String userId, String deviceId);

    /**
     * 添加本地软件
      * * @参数 software
      * * @申报 添加结果
     * @param software software
     * @return 添加本地software的结果
     */
    ReturnResult<SoftSoftware> addLocalSoftware(SoftSoftware software);

    /**
     * 获取本地软件列表（不分页）
     *
     * @param keyword 关键词过滤（可选）
     * @return 软件列表
     */
    java.util.List<SoftSoftware> listLocalSoftware(String keyword);

    /**
      * 获取默认本地软件源标识
     *
     * @return 本地软件源ID
     */
    String getLocalSourceId();
}
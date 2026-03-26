package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingPreviewExtension;
import com.chua.starter.proxy.support.pojo.PreviewExtensionConfigVo;

import java.util.List;

/**
 * 预览扩展名配置Service接口
 *
 * @author CH
 * @since 2024/12/08
 */
public interface SystemServerSettingPreviewExtensionService extends IService<SystemServerSettingPreviewExtension> {

    /**
     * 根据服务器ID获取配置
     *
     * @param serverId 服务器ID
     * @return 配置VO
     */
    PreviewExtensionConfigVo getByServerIdForPreviewExtension(Integer serverId);

    /**
     * 保存配置
     *
     * @param configVo 配置VO
     * @return 操作结果
     */
    ReturnResult<Boolean> saveConfigForPreviewExtension(PreviewExtensionConfigVo configVo);

    /**
     * 添加禁用的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    ReturnResult<Boolean> addDisabledExtensionsForPreviewExtension(Integer serverId, List<String> extensions);

    /**
     * 移除禁用的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    ReturnResult<Boolean> removeDisabledExtensionsForPreviewExtension(Integer serverId, List<String> extensions);

    /**
     * 添加允许的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    ReturnResult<Boolean> addAllowedExtensionsForPreviewExtension(Integer serverId, List<String> extensions);

    /**
     * 移除允许的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    ReturnResult<Boolean> removeAllowedExtensionsForPreviewExtension(Integer serverId, List<String> extensions);

    /**
     * 设置预览模式
     *
     * @param serverId      服务器ID
     * @param whitelistMode 是否白名单模式
     * @return 操作结果
     */
    ReturnResult<Boolean> setPreviewModeForPreviewExtension(Integer serverId, Boolean whitelistMode);

    /**
     * 清空配置
     *
     * @param serverId 服务器ID
     * @return 操作结果
     */
    ReturnResult<Boolean> clearConfigForPreviewExtension(Integer serverId);
}





package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.eduai.model.EduFunConfig;

import java.util.List;

/**
 * 趣味模块配置服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduFunConfigService extends IService<EduFunConfig> {

    /**
     * 获取所有启用的趣味模块配置
     *
     * @return 启用的配置列表
     */
    List<EduFunConfig> listEnabled();

    /**
     * 根据模块编码获取配置
     *
     * @param moduleCode 模块编码
     * @return 配置
     */
    EduFunConfig getByModuleCode(String moduleCode);

    /**
     * 更新模块启用状态
     *
     * @param moduleCode 模块编码
     * @param enabled    是否启用
     * @return 是否成功
     */
    boolean updateEnabled(String moduleCode, Integer enabled);
}

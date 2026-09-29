package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
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
     * 获取全部配置（含禁用），非管理员仅返回本人归属行与系统预置行
     *
     * @return 配置列表
     */
    List<EduFunConfig> listAllScoped();

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

    /**
     * 查询指定学段启用中的模块清单（学生端首页用）
     *
     * @param gradeLevel 学段：PRIMARY / JUNIOR / SENIOR
     * @return 该学段的模块列表，按 sort_order 升序
     */
    List<EduFunConfig> listByLevel(String gradeLevel);
}

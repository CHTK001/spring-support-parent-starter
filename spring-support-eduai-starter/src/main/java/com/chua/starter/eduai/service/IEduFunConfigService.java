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
     * 根据模块编码获取配置。
     *
     * @param moduleCode 模块编码
     * @param gradeLevel 学段；为空时不限（优先通用行）
     * @return 配置
     */
    EduFunConfig getByModuleCode(String moduleCode, String gradeLevel);

    /**
     * 更新模块启用状态。
     *
     * @param moduleCode 模块编码
     * @param gradeLevel 学段；为空时不限（优先通用行）
     * @param enabled    是否启用
     * @return 是否成功
     */
    boolean updateEnabled(String moduleCode, String gradeLevel, Integer enabled);

    /**
     * 门户菜单清单（含停用项，供学生端/教师端识别停用）。
     *
     * <p>只返回门户需要的字段（编码/名称/路径/学段/启停/排序），
     * 不暴露 configJson、归属人等管理端信息。</p>
     *
     * @param gradeLevel 学段：PRIMARY / JUNIOR / SENIOR / TEACHER
     * @return 该学段的菜单清单，按排序号升序
     */
    List<EduFunConfig> listPortalMenu(String gradeLevel);

    /**
     * 查询指定学段启用中的模块清单（学生端首页用）
     *
     * @param gradeLevel 学段：PRIMARY / JUNIOR / SENIOR / TEACHER
     * @return 该学段的模块列表，按 sort_order 升序
     */
    List<EduFunConfig> listByLevel(String gradeLevel);

    /**
     * 保存单条配置（含归属与学段校验）。
     *
     * @param config 配置
     * @return 保存后的配置（新建行会带回 configId）
     */
    EduFunConfig saveScoped(EduFunConfig config);

    /**
     * 批量保存配置（同一事务，全成或全败）。
     *
     * <p>门户菜单一次「保存」会改多行（字段 + 排序），逐条提交会在中途失败时留下
     * 半保存状态；这里放在同一事务里，调用方失败后可以安全保留本地编辑重试。</p>
     *
     * @param configs 配置列表
     * @return 保存后的配置列表（新建行会带回 configId）
     */
    List<EduFunConfig> saveBatchScoped(List<EduFunConfig> configs);
}

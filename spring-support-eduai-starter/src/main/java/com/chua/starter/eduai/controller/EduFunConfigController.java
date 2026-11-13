package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.eduai.model.EduFunConfig;
import com.chua.starter.eduai.service.IEduFunConfigService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 趣味模块配置控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/fun")
@RequiredArgsConstructor
@Tag(name = "趣味模块配置")
public class EduFunConfigController {

    private final IEduFunConfigService funConfigService;

    /**
     * 列出Enabled。
     *
     * @return Return结果 对象
     */
    @Operation(summary = "获取所有启用的趣味模块")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @GetMapping("/list")
    public ReturnResult<List<EduFunConfig>> listEnabled() {
        return ReturnResult.ok(funConfigService.listEnabled());
    }

    /**
     * 列出全部。
     *
     * @return Return结果 对象
     */
    @Operation(summary = "获取所有趣味模块（含禁用）")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @GetMapping("/list-all")
    public ReturnResult<List<EduFunConfig>> listAll() {
        return ReturnResult.ok(funConfigService.listAllScoped());
    }

    /**
     * 门户菜单清单（学生端/教师端用）。
     *
     * <p>含停用项，只返回编码/名称/路径/学段/启停/排序，用来判断
     * 「从没配过」与「全部停用」，并过滤「全部功能」里的停用入口。</p>
     *
     * @param level 学段：PRIMARY / JUNIOR / SENIOR / TEACHER
     * @return 该学段的菜单清单
     */
    @Operation(summary = "门户菜单清单（含停用）")
    @GetMapping("/portal-menu")
    public ReturnResult<List<EduFunConfig>> portalMenu(@RequestParam String level) {
        return ReturnResult.ok(funConfigService.listPortalMenu(level));
    }

    /**
     * 指定学段的启用模块清单（学生端首页卡片用）
     *
     * <p>学生端按自己的学段取这份清单渲染功能卡；后台在「模块配置」里
     * 按学段增删改排序，学生端不需要改代码。</p>
     *
     * @param gradeLevel 学段：PRIMARY / JUNIOR / SENIOR
     * @return 该学段的模块列表
     */
    @Operation(summary = "指定学段的模块清单（学生端）")
    @GetMapping("/modules")
    public ReturnResult<List<EduFunConfig>> modules(@RequestParam String gradeLevel) {
        return ReturnResult.ok(funConfigService.listByLevel(gradeLevel));
    }

    /**
     * detail。
     *
     * @param moduleCode module编码，不允许为 null
     * @param gradeLevel 学段（可选）；同编码在多个学段各有一行时用它精确定位
     * @return Return结果 对象
     */
    @Operation(summary = "根据模块编码获取配置")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @GetMapping("/detail/{moduleCode}")
    public ReturnResult<EduFunConfig> detail(@PathVariable String moduleCode,
                                             @RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(funConfigService.getByModuleCode(moduleCode, gradeLevel));
    }

    @Operation(summary = "更新模块启用状态")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @PutMapping("/toggle/{moduleCode}")
    public ReturnResult<Boolean> toggle(@PathVariable String moduleCode,
                                         @RequestParam(required = false) String gradeLevel,
                                         @RequestParam Integer enabled) {
        return ReturnResult.ok(funConfigService.updateEnabled(moduleCode, gradeLevel, enabled));
    }

    /**
     * 保存。
     *
     * @param config 配置，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "保存趣味模块配置")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody EduFunConfig config) {
        funConfigService.saveScoped(config);
        return ReturnResult.ok(true);
    }

    /**
     * 批量保存（同一事务）。
     *
     * <p>门户菜单一次保存会改多行；逐条提交中途失败会留下半保存状态，
     * 这里整批成功或整批回滚，调用方失败后可以原样保留本地编辑重试。</p>
     *
     * @param configs 配置列表
     * @return 保存后的配置（新建行带回 configId）
     */
    @Operation(summary = "批量保存趣味模块配置")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @PostMapping("/batch-save")
    public ReturnResult<List<EduFunConfig>> batchSave(@RequestBody List<EduFunConfig> configs) {
        return ReturnResult.ok(funConfigService.saveBatchScoped(configs));
    }

    /**
     * 删除。
     *
     * @param configId 配置ID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "删除趣味模块配置")
    @Permission(role = {"ADMIN", "SUPER_ADMIN"})
    @DeleteMapping("/delete/{configId}")
    public ReturnResult<Boolean> delete(@PathVariable Long configId) {
        EduFunConfig exists = funConfigService.getById(configId);
        if (exists == null) {
            return ReturnResult.ok(false);
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        return ReturnResult.ok(funConfigService.removeById(configId));
    }
}

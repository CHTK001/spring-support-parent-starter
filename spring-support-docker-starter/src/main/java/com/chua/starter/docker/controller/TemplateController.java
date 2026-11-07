package com.chua.starter.docker.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.docker.model.DockerTemplate;
import com.chua.starter.docker.service.TemplateService;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 容器模板控制器 —— 保存的 {@code docker run} 配置 CRUD（不带 hostId）。
 *
 * <p>模板不含主机维度：同一套配置可以下发到任意主机。安装容器时把
 * {@code templateId} 放进创建请求即可由服务端回填未传字段。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Tag(name = "Docker 模板", description = "容器模板 CRUD（docker run 配置）")
// 显式 bean 名：template-starter 的 TemplateController(/api/template) 类名相同，
// 两个 starter 被同一应用（monitor）同时引入时默认 bean 名都是 templateController，
// 组件扫描会抛 ConflictingBeanDefinitionException 导致启动失败。
@RestController("dockerTemplateController")
@RequestMapping(ServerConstants.API_PREFIX + "/docker/templates")
@RequiredArgsConstructor
public class TemplateController {

    /**
     * 模板服务
     */
    private final TemplateService templateService;

    /**
     * 模板列表
     *
     * @return 模板列表
     */
    @Operation(summary = "模板列表")
    @GetMapping
    public ReturnResult<List<DockerTemplate>> templates() {
        return ReturnResult.ok(templateService.list());
    }

    /**
     * 新增 / 更新模板
     *
     * @param template 模板（id 为空表示新增）
     * @return 保存后的模板
     */
    @Operation(summary = "新增或更新模板")
    @PostMapping
    public ReturnResult<DockerTemplate> saveTemplate(@RequestBody DockerTemplate template) {
        try {
            return ReturnResult.ok(templateService.save(template));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     *
     * @param id 模板标识
     * @return 是否删除
     */
    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> deleteTemplate(@PathVariable String id) {
        try {
            return ReturnResult.ok(templateService.delete(id));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}

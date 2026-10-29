package com.chua.starter.docker.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.docker.service.RegistryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 镜像库控制器 —— 镜像库 CRUD、连通性测试与在线检索（不带 hostId）。
 *
 * <p>路径前缀与 Docker 主机维度接口一致（{@code /ms/server/docker}）：镜像库属于
 * 全局配置，与具体主机无关，因此不放在 {@code {hostId}} 之下。密码只以
 * {@code hasPassword} 暴露，编辑时回传空串或 {@code ******} 表示不修改。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Tag(name = "Docker 镜像库", description = "镜像库 CRUD / 连通性测试 / 在线检索 / 标签")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/docker")
@RequiredArgsConstructor
public class RegistryController {

    /**
     * 镜像库服务
     */
    private final RegistryService registryService;

    /**
     * 镜像库列表（不含密码）
     *
     * @return 视图列表
     */
    @Operation(summary = "镜像库列表")
    @GetMapping("/registries")
    public ReturnResult<List<Map<String, Object>>> registries() {
        return ReturnResult.ok(registryService.list());
    }

    /**
     * 新增 / 更新镜像库
     *
     * @param body id/name/type/url/username/password/namespace/enabled
     * @return 保存后的视图
     */
    @Operation(summary = "新增或更新镜像库")
    @PostMapping("/registries")
    public ReturnResult<Map<String, Object>> saveRegistry(@RequestBody Map<String, Object> body) {
        try {
            return ReturnResult.ok(registryService.save(body));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除镜像库（内置库不可删除）
     *
     * @param id 库标识
     * @return 是否删除
     */
    @Operation(summary = "删除镜像库")
    @DeleteMapping("/registries/{id}")
    public ReturnResult<Boolean> deleteRegistry(@PathVariable String id) {
        try {
            return ReturnResult.ok(registryService.delete(id));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 测试镜像库连通性
     *
     * @param id 库标识
     * @return {ok, message}
     */
    @Operation(summary = "测试镜像库连通性")
    @PostMapping("/registries/{id}/test")
    public ReturnResult<Map<String, Object>> testRegistry(@PathVariable String id) {
        return ReturnResult.ok(registryService.test(id));
    }

    /**
     * 可用的镜像库类型（SPI 提供）
     *
     * @return [{type, displayName}]
     */
    @Operation(summary = "镜像库类型列表")
    @GetMapping("/registries/types")
    public ReturnResult<List<Map<String, String>>> registryTypes() {
        return ReturnResult.ok(registryService.types());
    }

    /**
     * 镜像标签
     *
     * @param id    库标识
     * @param image 镜像名（不含 registry host）
     * @return 标签列表
     */
    @Operation(summary = "镜像标签列表")
    @GetMapping("/registries/{id}/tags")
    public ReturnResult<List<String>> tags(@PathVariable String id,
                                           @RequestParam String image) {
        try {
            return ReturnResult.ok(registryService.tags(id, image));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 在线检索镜像（registryId 为空时并发检索全部启用镜像库）
     *
     * @param keyword    关键词（≥2 字符）
     * @param registryId 指定镜像库，可空
     * @param limit      条数上限
     * @return 统一结构：registryId/registryName/name/description/stars/official/pullRef
     */
    @Operation(summary = "在线检索镜像")
    @GetMapping("/images/search")
    public ReturnResult<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String registryId,
                                                          @RequestParam(required = false) Integer limit) {
        try {
            return ReturnResult.ok(registryService.search(keyword, registryId, limit));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}

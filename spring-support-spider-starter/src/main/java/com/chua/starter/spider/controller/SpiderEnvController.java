package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderEnv;
import com.chua.starter.spider.service.SpiderEnvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 环境变量管理接口。
 *
 * <p>提供服务端环境变量的增删改查，爬虫运行期通过 {@code {{env.KEY}}}
 * 语法自动插值，路径前缀 {@code /spider-api/spider/envs}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "环境变量管理")
@RestController
@RequestMapping("${spider.path:/spider-api/spider}/envs")
public class SpiderEnvController {

    /**
     * 环境变量服务
     */
    private final SpiderEnvService envService;

    /**
     * 构造方法。
     *
     * @param envService 环境变量服务
     */
    public SpiderEnvController(SpiderEnvService envService) {
        this.envService = envService;
    }

    /**
     * 查询全部环境变量。
     *
     * @return 环境变量列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部环境变量")
    public ReturnResult<List<SpiderEnv>> list() {
        return ReturnResult.ok(envService.list());
    }

    /**
     * 查询已启用环境变量映射。
     *
     * @return 环境变量 k-v 映射
     */
    @GetMapping("/map-enabled")
    @Operation(summary = "查询已启用环境变量映射")
    public ReturnResult<Map<String, String>> mapEnabled() {
        return ReturnResult.ok(envService.mapEnabled());
    }

    /**
     * 新增环境变量。
     *
     * @param entity 环境变量
     * @return 是否成功
     */
    @PostMapping
    @Operation(summary = "新增环境变量")
    public ReturnResult<Boolean> save(@RequestBody SpiderEnv entity) {
        return ReturnResult.ok(envService.save(entity));
    }

    /**
     * 更新环境变量。
     *
     * @param entity 环境变量
     * @return 是否成功
     */
    @PutMapping
    @Operation(summary = "更新环境变量")
    public ReturnResult<Boolean> update(@RequestBody SpiderEnv entity) {
        return ReturnResult.ok(envService.updateById(entity));
    }

    /**
     * 删除环境变量。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除环境变量")
    public ReturnResult<Boolean> remove(@PathVariable Long id) {
        return ReturnResult.ok(envService.removeById(id));
    }
}
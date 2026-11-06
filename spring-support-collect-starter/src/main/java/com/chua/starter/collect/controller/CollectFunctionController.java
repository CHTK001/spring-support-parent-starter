package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.dto.CollectFunctionTestDTO;
import com.chua.starter.collect.entity.CollectFunction;
import com.chua.starter.collect.service.CollectFunctionService;
import com.chua.starter.collect.vo.CollectFunctionTestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自定义函数接口。
 *
 * <p>函数登记后进的是编译校验这一关，存下来即可在脚本里 {@code fn.名字(...)} 调用，
 * 也能在任意节点参数里写 {@code ${名字(...)}}，两条路共用同一张函数表。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-自定义函数")
@RestController
@RequestMapping("/v2/collect/function")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectFunctionController {

    /**
     * 函数服务。
     */
    private final CollectFunctionService functionService;

    /**
     * 函数列表。
     *
     * @param keyword 名称或说明关键字
     * @return 函数列表
     */
    @Operation(summary = "函数列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectFunction>> list(@RequestParam(required = false) String keyword) {
        return ReturnResult.ok(functionService.listFunctions(keyword));
    }

    /**
     * 新增函数。
     *
     * @param function 函数定义
     * @return 保存后的函数
     */
    @Operation(summary = "新增函数")
    @PostMapping("/save")
    public ReturnResult<CollectFunction> save(@RequestBody CollectFunction function) {
        return ReturnResult.ok(functionService.addFunction(function));
    }

    /**
     * 修改函数。
     *
     * @param function 函数定义
     * @return 是否成功
     */
    @Operation(summary = "修改函数")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody CollectFunction function) {
        return ReturnResult.ok(functionService.editFunction(function));
    }

    /**
     * 删除函数。
     *
     * @param functionId 函数ID
     * @return 是否成功
     */
    @Operation(summary = "删除函数")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer functionId) {
        return ReturnResult.ok(functionService.deleteFunction(functionId));
    }

    /**
     * 试跑函数。
     *
     * @param dto 试跑入参
     * @return 试跑结果
     */
    @Operation(summary = "试跑函数")
    @PostMapping("/test")
    public ReturnResult<CollectFunctionTestVO> test(@RequestBody CollectFunctionTestDTO dto) {
        return ReturnResult.ok(functionService.testFunction(dto));
    }
}

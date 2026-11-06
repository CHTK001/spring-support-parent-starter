package com.chua.starter.lowcode.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.service.LowFormDirectoryService;
import com.chua.starter.lowcode.vo.LowFormDirectoryVO;
import com.chua.starter.lowcode.vo.LowFormOptionVO;
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
 * 低代码-目录接口。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "低代码-目录")
@RestController
@RequestMapping("/v2/lowcode/directory")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.lowcode", name = "enable", havingValue = "true", matchIfMissing = true)
public class LowFormDirectoryController {

    /**
     * 目录服务。
     */
    private final LowFormDirectoryService directoryService;

    /**
     * 目录树。
     *
     * @return 树形目录
     */
    @Operation(summary = "目录树")
    @GetMapping("/tree")
    public ReturnResult<List<LowFormDirectoryVO>> tree() {
        return ReturnResult.ok(directoryService.tree());
    }

    /**
     * 目录下拉选项。
     *
     * @return 选项列表
     */
    @Operation(summary = "目录下拉选项")
    @GetMapping("/options")
    public ReturnResult<List<LowFormOptionVO>> options() {
        return ReturnResult.ok(directoryService.options());
    }

    /**
     * 新增目录。
     *
     * @param directory 目录
     * @return 保存后的目录
     */
    @Operation(summary = "新增目录")
    @PostMapping("/save")
    public ReturnResult<LowFormDirectory> save(@RequestBody LowFormDirectory directory) {
        return ReturnResult.ok(directoryService.addDirectory(directory));
    }

    /**
     * 修改目录。
     *
     * @param directory 目录
     * @return 是否成功
     */
    @Operation(summary = "修改目录")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody LowFormDirectory directory) {
        return ReturnResult.ok(directoryService.editDirectory(directory));
    }

    /**
     * 删除目录，存在子目录或表单/页面时拒绝。
     *
     * @param directoryId 目录ID
     * @return 是否成功
     */
    @Operation(summary = "删除目录")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer directoryId) {
        return ReturnResult.ok(directoryService.deleteDirectory(directoryId));
    }
}

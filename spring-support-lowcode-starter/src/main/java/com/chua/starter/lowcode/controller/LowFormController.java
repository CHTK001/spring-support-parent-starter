package com.chua.starter.lowcode.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.service.LowFormService;
import com.chua.starter.lowcode.vo.LowFormVO;
import com.chua.starter.lowcode.vo.LowFormVersionVO;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 低代码-表单接口。
 *
 * <p>示例：设计器用 /code/{formCode} 回读，运行期用 /api/{formCode} 取已发布 Schema。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "低代码-表单")
@RestController
@RequestMapping("/v2/lowcode/form")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.lowcode", name = "enable", havingValue = "true", matchIfMissing = true)
public class LowFormController {

    /**
     * 表单服务。
     */
    private final LowFormService formService;

    /**
     * 表单分页列表。
     *
     * @param query     分页参数
     * @param condition 查询条件
     * @return 分页结果
     */
    @Operation(summary = "表单分页列表")
    @GetMapping("/page")
    public ReturnPageResult<LowFormVO> page(Query<LowForm> query,
                                             LowForm condition) {
        return PageResultUtils.ok(formService.page(query.createPage(), condition));
    }

    /**
     * 表单详情。
     *
     * @param formId 表单ID
     * @return 表单详情
     */
    @Operation(summary = "表单详情")
    @GetMapping("/detail")
    public ReturnResult<LowFormVO> detail(@RequestParam Integer formId) {
        return ReturnResult.ok(formService.detail(formId));
    }

    /**
     * 通过表单编码查询详情（供设计器回读使用）。
     *
     * @param formCode 表单编码
     * @return 表单详情
     */
    @Operation(summary = "按编码查询详情")
    @GetMapping("/code/{formCode}")
    public ReturnResult<LowFormVO> detailByCode(@PathVariable String formCode) {
        return ReturnResult.ok(formService.detailByCode(formCode));
    }

    /**
     * 新增表单。
     *
     * @param form 表单
     * @return 保存后的表单
     */
    @Operation(summary = "新增表单")
    @PostMapping("/save")
    public ReturnResult<LowForm> save(@RequestBody LowForm form) {
        return ReturnResult.ok(formService.saveForm(form));
    }

    /**
     * 修改表单。
     *
     * @param form 表单
     * @return 是否成功
     */
    @Operation(summary = "修改表单")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody LowForm form) {
        return ReturnResult.ok(formService.updateForm(form));
    }

    /**
     * 删除表单。
     *
     * @param formId 表单ID
     * @return 是否成功
     */
    @Operation(summary = "删除表单")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer formId) {
        return ReturnResult.ok(formService.deleteForm(formId));
    }

    /**
     * 发布表单：生成版本快照、版本号 +1、设置发布状态并启用。
     *
     * @param formId 表单ID
     * @return 是否成功
     */
    @Operation(summary = "发布表单")
    @PostMapping("/publish")
    public ReturnResult<Boolean> publish(@RequestParam Integer formId) {
        return ReturnResult.ok(formService.publishForm(formId));
    }

    /**
     * 回滚到指定版本：恢复 Schema、版本号、发布状态。
     *
     * @param versionId 版本ID
     * @return 是否成功
     */
    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback")
    public ReturnResult<Boolean> rollback(@RequestParam Integer versionId) {
        return ReturnResult.ok(formService.rollbackToVersion(versionId));
    }

    /**
     * 表单的版本列表。
     *
     * @param formId 表单ID
     * @return 版本列表
     */
    @Operation(summary = "版本列表")
    @GetMapping("/versions")
    public ReturnResult<List<LowFormVersionVO>> versions(@RequestParam Integer formId) {
        return ReturnResult.ok(formService.versions(formId));
    }

    /**
     * 渲染器专用接口：获取已发布表单的 Schema，基于 code 查询。
     *
     * @param formCode 表单编码
     * @return 已发布 Schema，未发布返回 null
     */
    @Operation(summary = "已发布 Schema")
    @GetMapping("/api/{formCode}")
    public ReturnResult<String> api(@PathVariable String formCode) {
        return ReturnResult.ok(formService.publishedSchema(formCode));
    }
}

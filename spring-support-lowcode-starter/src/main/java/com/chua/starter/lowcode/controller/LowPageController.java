package com.chua.starter.lowcode.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.lowcode.entity.LowFormPage;
import com.chua.starter.lowcode.service.LowPageService;
import com.chua.starter.lowcode.vo.LowFormPageVersionVO;
import com.chua.starter.lowcode.vo.LowFormPageVO;
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
 * 低代码-页面接口。
 *
 * <p>示例：设计器用 /code/{pageCode} 回读，运行期用 /api/{pageCode} 取已发布 Schema。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "低代码-页面")
@RestController
@RequestMapping("/v2/lowcode/page")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.lowcode", name = "enable", havingValue = "true", matchIfMissing = true)
public class LowPageController {

    /**
     * 页面服务。
     */
    private final LowPageService pageService;

    /**
     * 页面分页列表。
     *
     * @param query     分页参数
     * @param condition 查询条件
     * @return 分页结果
     */
    @Operation(summary = "页面分页列表")
    @GetMapping("/page")
    public ReturnPageResult<LowFormPageVO> page(Query<LowFormPage> query,
                                                 LowFormPage condition) {
        return PageResultUtils.ok(pageService.page(query.createPage(), condition));
    }

    /**
     * 页面详情。
     *
     * @param pageId 页面ID
     * @return 页面详情
     */
    @Operation(summary = "页面详情")
    @GetMapping("/detail")
    public ReturnResult<LowFormPageVO> detail(@RequestParam Integer pageId) {
        return ReturnResult.ok(pageService.detail(pageId));
    }

    /**
     * 通过页面编码查询详情（供设计器回读使用）。
     *
     * @param pageCode 页面编码
     * @return 页面详情
     */
    @Operation(summary = "按编码查询详情")
    @GetMapping("/code/{pageCode}")
    public ReturnResult<LowFormPageVO> detailByCode(@PathVariable String pageCode) {
        return ReturnResult.ok(pageService.detailByCode(pageCode));
    }

    /**
     * 新增页面。
     *
     * @param page 页面
     * @return 保存后的页面
     */
    @Operation(summary = "新增页面")
    @PostMapping("/save")
    public ReturnResult<LowFormPage> save(@RequestBody LowFormPage page) {
        return ReturnResult.ok(pageService.savePage(page));
    }

    /**
     * 修改页面。
     *
     * @param page 页面
     * @return 是否成功
     */
    @Operation(summary = "修改页面")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody LowFormPage page) {
        return ReturnResult.ok(pageService.updatePage(page));
    }

    /**
     * 删除页面。
     *
     * @param pageId 页面ID
     * @return 是否成功
     */
    @Operation(summary = "删除页面")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer pageId) {
        return ReturnResult.ok(pageService.deletePage(pageId));
    }

    /**
     * 发布页面：生成版本快照、版本号 +1、设置发布状态并启用。
     *
     * @param pageId 页面ID
     * @return 是否成功
     */
    @Operation(summary = "发布页面")
    @PostMapping("/publish")
    public ReturnResult<Boolean> publish(@RequestParam Integer pageId) {
        return ReturnResult.ok(pageService.publishPage(pageId));
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
        return ReturnResult.ok(pageService.rollbackToVersion(versionId));
    }

    /**
     * 启用/停用页面。停用后运行期接口不再返回 Schema。
     *
     * @param pageId 页面ID
     * @param status 0:停用, 1:启用
     * @return 是否成功
     */
    @Operation(summary = "启用/停用页面")
    @PostMapping("/status")
    public ReturnResult<Boolean> changeStatus(@RequestParam Integer pageId, @RequestParam Integer status) {
        return ReturnResult.ok(pageService.changeStatus(pageId, status));
    }

    /**
     * 撤销发布：发布状态回到未发布，运行期立即取不到 Schema。
     *
     * @param pageId 页面ID
     * @return 是否成功
     */
    @Operation(summary = "撤销发布")
    @PostMapping("/unpublish")
    public ReturnResult<Boolean> unpublish(@RequestParam Integer pageId) {
        return ReturnResult.ok(pageService.unpublish(pageId));
    }

    /**
     * 页面的版本列表。
     *
     * @param pageId 页面ID
     * @return 版本列表
     */
    @Operation(summary = "版本列表")
    @GetMapping("/versions")
    public ReturnResult<List<LowFormPageVersionVO>> versions(@RequestParam Integer pageId) {
        return ReturnResult.ok(pageService.versions(pageId));
    }

    /**
     * 渲染器专用接口：获取已发布页面的 Schema，基于 code 查询。
     *
     * @param pageCode 页面编码
     * @return 已发布 Schema，未发布返回 null
     */
    @Operation(summary = "已发布 Schema")
    @GetMapping("/api/{pageCode}")
    public ReturnResult<String> api(@PathVariable String pageCode) {
        return ReturnResult.ok(pageService.publishedSchema(pageCode));
    }
}

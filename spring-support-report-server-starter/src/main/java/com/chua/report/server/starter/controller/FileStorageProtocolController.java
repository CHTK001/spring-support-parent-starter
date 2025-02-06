package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.report.server.starter.entity.FileStorageProtocol;
import com.chua.report.server.starter.service.FileStorageProtocolService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * OSS代理
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/file/storage/protocol")
@Tag(name = "文件存储 - 协议")
@RequiredArgsConstructor
public class FileStorageProtocolController {

    private final FileStorageProtocolService fileStorageProtocolService;
    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        Set<String> ids = Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id);
        if(ids.isEmpty()) {
            return ReturnResult.ok();
        }

        if(ids.size() == 1) {
            return ReturnResult.of(fileStorageProtocolService.removeById(ids.iterator().next()));
        }

        return ReturnResult.of(fileStorageProtocolService.removeBatchByIds(ids));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody FileStorageProtocol t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }


        return ReturnResult.of(fileStorageProtocolService.updateFor(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<FileStorageProtocol> save(@Validated(AddGroup.class) @RequestBody FileStorageProtocol t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        t.setFileStorageProtocolUaOpen(0);
        t.setFileStorageProtocolPluginOpen(0);
        t.setFileStorageProtocolSettingOpen(0);
        if(null == t.getFileStorageProtocolPreviewOrDownload()) {
            t.setFileStorageProtocolPreviewOrDownload(0);
        }
        fileStorageProtocolService.save(t);
        return ReturnResult.ok(t);
    }

    /**
     *
     *
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<FileStorageProtocol> page(Query<FileStorageProtocol> page, FileStorageProtocol entity) {
        Page<FileStorageProtocol> tPage = fileStorageProtocolService.page(page.createPage());
        return PageResultUtils.ok(tPage);
    }
    /**
     * 开始监控代理。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "开始")
    @GetMapping("start")
    public ReturnResult<Boolean> start(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        FileStorageProtocol fileStorageProtocol = fileStorageProtocolService.getById(id);
        // 检查监控代理实例是否存在
        if(null == fileStorageProtocol) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已开启，则返回错误信息
        if(null != fileStorageProtocol.getFileStorageProtocolStatus() && 1 == fileStorageProtocol.getFileStorageProtocolStatus()) {
            return ReturnResult.error("代理已开启");
        }
        // 开启监控代理，并返回操作结果
        return fileStorageProtocolService.start(fileStorageProtocol);
    }

    /**
     * 停止监控代理。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "停止")
    @GetMapping("stop")
    public ReturnResult<Boolean> stop(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        FileStorageProtocol fileStorageProtocol = fileStorageProtocolService.getById(id);
        // 检查监控代理实例是否存在
        if(null == fileStorageProtocol) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != fileStorageProtocol.getFileStorageProtocolStatus() && 0 == fileStorageProtocol.getFileStorageProtocolStatus()) {
            return ReturnResult.error("代理已停止");
        }
        // 停止监控代理，并返回操作结果
        return fileStorageProtocolService.stop(fileStorageProtocol);
    }

}

package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import com.chua.starter.unified.server.support.service.UnifiedPatchService;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

import static com.chua.common.support.lang.code.ReturnCode.PARAM_ERROR;

/**
 * 当前只支持DECVM
 * @author CH
 */
@RestController
@RequestMapping("v1/patch")
@AllArgsConstructor
public class UnifiedPatchController {


    private final UnifiedPatchService unifiedPatchService;


    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ReturnPageResult<Page<UnifiedPatch>> page(DelegatePage<UnifiedPatch> page, @Valid UnifiedPatch entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResult.ok(unifiedPatchService.pageItems(page.createPage(), entity));
    }
    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @DeleteMapping("delete")
    public ResultData<Boolean> delete(String id) {
        if (null == id) {
            return ResultData.failure(PARAM_ERROR, "主键不能为空");
        }
        return ResultData.success(unifiedPatchService.removePatch(id));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    @ResponseBody
    public ResultData<Boolean> save(@Valid @RequestBody UnifiedPatch t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(unifiedPatchService.saveOrUpdate(t));
    }

    /**
     * 上传补丁到各个客户端
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("upload")
    @ResponseBody
    public ReturnResult<ErrorResult> upload(@RequestBody UnifiedPatch t){
        if(CollectionUtils.isEmpty(t.getExecutorIds())) {
            return ReturnResult.failure(PARAM_ERROR, "请选择执行器");
        }

        if(null == t.getUnifiedPatchId()) {
            return ReturnResult.failure(PARAM_ERROR, "补丁编号不能为空");
        }
        return ReturnResult.success(unifiedPatchService.upload(t));
    }
    /**
     * 上传补丁
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("loadPatch")
    @ResponseBody
    public ReturnResult<ErrorResult> loadPatch(UnifiedPatch t, @RequestParam("file") MultipartFile multipartFile){
        if(null == multipartFile) {
            return ReturnResult.failure(PARAM_ERROR, "补丁不能为空");
        }

        if(null == t.getUnifiedPatchId()) {
            return ReturnResult.failure(PARAM_ERROR, "补丁编号不能为空");
        }
        return ReturnResult.success(unifiedPatchService.uploadPatch(t, multipartFile));
    }
    /**
     * 卸载补丁
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("unloadPatch")
    @ResponseBody
    public ResultData<Boolean> unloadPatch(@RequestBody UnifiedPatch t ) {

        if(null == t.getUnifiedPatchId()) {
            return ResultData.failure(PARAM_ERROR, "补丁编号不能为空");
        }
        return ResultData.success(unifiedPatchService.unloadPatch(t));
    }
    /**
     * 卸载补丁
     *
     * @param t 实体
     * @return 分页结果
     */
//    @PostMapping("downloadPatch")
//    @ResponseBody
//    public ReturnMedia unloadPatch(@RequestBody UnifiedPatch t ) {
//
//        if(null == t.getUnifiedPatchId()) {
//            return ReturnMedia.failure(PARAM_ERROR, "补丁编号不能为空");
//        }
//        return ResultData.success(unifiedPatchService.unloadPatch(t));
//    }

}

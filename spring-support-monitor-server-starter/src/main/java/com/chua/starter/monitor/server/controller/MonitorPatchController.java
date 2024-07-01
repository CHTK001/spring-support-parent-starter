package com.chua.starter.monitor.server.controller;


import com.chua.common.support.annotations.Group;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorPatch;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorPatchService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/patch")
@Tag(name = "补丁")
@RequiredArgsConstructor
public class MonitorPatchController extends AbstractSwaggerController<MonitorPatchService, MonitorPatch> {

    @Getter
    private final MonitorPatchService service;
    @Resource
    private final MonitorAppService monitorAppService;

    @Resource
    private final MonitorServerFactory monitorServerFactory;
    /**
     * 添加数据
     *
     * @param monitorPatch 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "下发配置")
    @PostMapping("upload")
    public ReturnResult<Boolean> upload(@Validated(Group.class) @RequestBody MonitorPatch monitorPatch, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorPatch config = service.getById(monitorPatch.getMonitorPatchId());
        if(null == config) {
            return ReturnResult.illegal("数据不存在");
        }

        List<MonitorRequest> heart = monitorServerFactory.getHeart(config.getMonitorPatchApp());
        if(ObjectUtils.isEmpty(heart)) {
            return ReturnResult.illegal("应用不存在");
        }

        config.setPatchFile(Base64.getEncoder().encodeToString(service.downloadPatch(config)));
        for (MonitorRequest monitorRequest : heart) {
            monitorAppService.upload(null, monitorRequest, Json.toJSONString(config), "PATCH", CommandType.REQUEST);
        }
        return ReturnResult.success();
    }


    /**
     * 上传补丁
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("loadPatch")
    @ResponseBody
    public ReturnResult<ErrorResult> loadPatch(MonitorPatch t, @RequestParam(name = "file", required = false) MultipartFile multipartFile){

        if(null == t.getMonitorPatchId()) {
            service.save(t);
        }
        if(null == multipartFile) {
            service.updateById(t);
            return ReturnResult.success();
        }
        return ReturnResult.success(service.uploadPatch(t, multipartFile));
    }
    /**
     * 卸载补丁
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("unloadPatch")
    @ResponseBody
    public ResultData<Boolean> unloadPatch(@RequestBody MonitorPatch t ) {

        if(null == t.getMonitorPatchId()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, "补丁编号不能为空");
        }
        return ResultData.success(service.unloadPatch(t));
    }

    /**
     * 卸载补丁
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("downloadPatch")
    public ResponseEntity<byte[]> downloadPatch(@RequestBody MonitorPatch t ) {

        if(null == t.getMonitorPatchId()) {
            throw new RuntimeException("补丁编号不能为空");
        }

        MonitorPatch unifiedPatch = service.getById(t.getMonitorPatchId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + unifiedPatch.getMonitorPatchPack()+ "\"")
                .body(service.downloadPatch(unifiedPatch))
                ;
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("deletePatch")
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        return ReturnResult.of(service.removePatch(id));
    }
}

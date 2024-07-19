package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
import com.chua.starter.monitor.server.service.MonitorProjectService;
import com.chua.starter.monitor.server.service.MonitorProjectVersionService;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 版本接口
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "版本接口")
@Slf4j
@RequestMapping("v1/version")
public class VersionController  {

    @Resource
    private MonitorProjectVersionService monitorProjectVersionService;

    @Getter
    @Resource
    private MonitorProjectService service;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    /**
     * 分页查询基础数据
     * @param page 分页请求对象
     * @param entity 基础数据实体对象
     * @return 分页查询结果
     */
    @GetMapping("page")
    @Operation(summary = "分页查询基础数据")
    public ReturnPageResult<MonitorProjectVersion> pageItem(PageRequest<MonitorProjectVersion> page, MonitorProjectVersion entity) {
        if(null == entity.getProjectId()) {
            return ReturnPageResult.illegal("数据不存在");
        }

        return ReturnPageResultUtils.ok(monitorProjectVersionService.page(page.createPage(), Wrappers.<MonitorProjectVersion>lambdaQuery(entity)));
    }

    /**
     * 保存版本数据
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "保存版本数据")
    public ReturnResult<MonitorProjectVersion> save(@Validated(AddGroup.class) @RequestBody MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        entity.setVersionCode(IdUtils.createUlid());
        entity.setVersionStatus(0);
        monitorProjectVersionService.save(entity);
        return ReturnResult.ok(entity);
    }

    /**
     * 保存版本数据
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PutMapping("update")
    @Operation(summary = "更新版本数据")
    public ReturnResult<MonitorProjectVersion> update(@Validated(UpdateGroup.class) @RequestBody MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorProjectVersion monitorProjectVersion = monitorProjectVersionService.getById(entity.getVersionId());
        if(null == monitorProjectVersion) {
            return ReturnResult.illegal("数据不存在");
        }
        entity.setVersionCode(null);
        ServiceProvider.of(Session.class).closeKeepExtension(monitorProjectVersion.getProjectId() + "terminal");
        monitorProjectVersionService.updateById(entity);
        return ReturnResult.ok(entity);
    }
    /**
     * 启动脚本
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PutMapping("start")
    @Operation(summary = "启动脚本")
    public ReturnResult<Boolean> start(@Validated(UpdateGroup.class)
                                                         @RequestBody MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        return ReturnResult.of(monitorProjectVersionService.start(entity));
    }
    /**
     * 保存版本数据
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PutMapping("stop")
    @Operation(summary = "停止脚本")
    public ReturnResult<Boolean> stop(@Validated(UpdateGroup.class)
                                                         @RequestBody MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        return ReturnResult.of(monitorProjectVersionService.stop(entity));
    }
    /**
     * 保存版本数据
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PutMapping("log")
    @Operation(summary = "获取日志")
    public ReturnResult<Boolean> log(@Validated(UpdateGroup.class)
                                                         @RequestBody MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        return ReturnResult.of(monitorProjectVersionService.log(entity));
    }

    /**
     * 删除版本数据
     * @param id 数据索引
     * @return 删除结果
     */
    @DeleteMapping("delete")
    @Operation(summary = "删除版本数据")
    public ReturnResult<Boolean> delete(String id) {
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.illegal("数据索引不能为空");
        }
        Set<String> strings = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(id);
        for (String string : strings) {
            ServiceProvider.of(Session.class).closeKeepExtension(string);
        }
        monitorProjectVersionService.removeBatchByIds(strings);
        return ReturnResult.ok(true);
    }
}

package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorProject;
import com.chua.starter.monitor.server.service.MonitorProjectService;
import com.chua.starter.monitor.server.service.MonitorProjectVersionService;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

@RestController
@SuppressWarnings("ALL")
@Tag(name = "项目接口")
@Slf4j
@RequestMapping("v1/project")
public class ProjectController {

    @Resource
    private MonitorProjectVersionService monitorProjectVersionService;

    @Getter
    @Resource
    private MonitorProjectService service;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;


    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorProject> page(PageRequest<MonitorProject> page, MonitorProject entity) {
        return PageResultUtils.ok(service.page(page.createPage(), Wrappers.lambdaQuery(entity)));
    }


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
        for (String s : ids) {
            ServiceProvider.of(Session.class).closeKeepExtension(s + "terminal");
        }
        return ReturnResult.of(service.removeBatchByIds(ids));
    }

    /**
     * 根据主键更新数据
     *
     * @param MonitorProject 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody MonitorProject MonitorProject , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        ServiceProvider.of(Session.class).closeKeepExtension(MonitorProject.getProjectId() + "terminal");
        return ReturnResult.of(service.updateById(MonitorProject));
    }

    /**
     * 添加数据
     *
     * @param MonitorProject 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<MonitorProject> save(@Validated(AddGroup.class) @RequestBody MonitorProject MonitorProject, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        service.save(MonitorProject);
        return ReturnResult.ok(MonitorProject);
    }

}

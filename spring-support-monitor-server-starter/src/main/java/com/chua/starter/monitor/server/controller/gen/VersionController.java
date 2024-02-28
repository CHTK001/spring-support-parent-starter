package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorProject;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
import com.chua.starter.monitor.server.service.MonitorProjectService;
import com.chua.starter.monitor.server.service.MonitorProjectVersionService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

@RestController
@SuppressWarnings("ALL")
@Tag(name = "脚本接口")
@Slf4j
@RequestMapping("v1/version")
public class VersionController extends AbstractSwaggerController<MonitorProjectService, MonitorProject> {

    @Resource
    private MonitorProjectVersionService monitorProjectVersionService;

    @Getter
    @Resource
    private MonitorProjectService service;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete2(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        Set<String> ids = Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id);
        return ReturnResult.of(getService().removeBatchByIds(ids));
    }
    /**
     * 分页查询基础数据
     * @param page 分页请求对象
     * @param entity 基础数据实体对象
     * @return 分页查询结果
     */
    @GetMapping("item/page")
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
    @PostMapping("item/save")
    @Operation(summary = "保存版本数据")
    public ReturnResult<MonitorProjectVersion> save(@Validated(AddGroup.class) MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        monitorProjectVersionService.save(entity);
        return ReturnResult.ok(entity);
    }

    /**
     * 保存版本数据
     * @param entity 版本数据实体对象
     * @param bindingResult 绑定结果对象
     * @return 保存结果
     */
    @PutMapping("item/update")
    @Operation(summary = "保存版本数据")
    public ReturnResult<MonitorProjectVersion> update(@Validated(UpdateGroup.class) MonitorProjectVersion entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        monitorProjectVersionService.updateById(entity);
        return ReturnResult.ok(entity);
    }

    /**
     * 删除版本数据
     * @param id 数据索引
     * @return 删除结果
     */
    @DeleteMapping("item/delete")
    @Operation(summary = "删除版本数据")
    public ReturnResult<Boolean> delete(String id) {
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.illegal("数据索引不能为空");
        }
        monitorProjectVersionService.removeBatchByIds(Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}

package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.server.entity.MonitorSysGenTemplate;
import com.chua.starter.monitor.server.service.MonitorSysGenTemplateService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模板控制器
 *
 * @author CH
 * @since 2023/10/15
 */
@RestController
@Tag(name = "模板接口")
@RequestMapping("v1/template")
@RequiredArgsConstructor
public class TemplateController {
    private final MonitorSysGenTemplateService sysGenTemplateService;


    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}>
     */
    @GetMapping("page")
    public ReturnPageResult<MonitorSysGenTemplate> page(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                         @RequestParam(value = "genId") String genId
                                         ) {

        Page<MonitorSysGenTemplate> genType = sysGenTemplateService.page(
                new Page<>(pageNum, pageSize),
                new MPJLambdaWrapper<MonitorSysGenTemplate>()
                        .selectAll(MonitorSysGenTemplate.class)
                        .eq(MonitorSysGenTemplate::getGenId, genId).or().isNull(MonitorSysGenTemplate::getGenId)
        );
        return PageResultUtils.ok(genType);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}>
     */
    @PostMapping("save")
    public ReturnResult<MonitorSysGenTemplate> save(@RequestBody MonitorSysGenTemplate sysGenTemplate) {
        sysGenTemplateService.save(sysGenTemplate);
        return ReturnResult.ok(sysGenTemplate);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}>
     */
    @PutMapping("update")
    public ReturnResult<MonitorSysGenTemplate> update(@RequestBody MonitorSysGenTemplate sysGenTemplate) {
        ServiceProvider.of(Session.class).closeKeepExtension(sysGenTemplate.getGenId() + "");

        sysGenTemplateService.updateById(sysGenTemplate);
        return ReturnResult.ok(sysGenTemplate);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}>
     */
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        sysGenTemplateService.removeById(id);
        return ReturnResult.ok(true);
    }
}

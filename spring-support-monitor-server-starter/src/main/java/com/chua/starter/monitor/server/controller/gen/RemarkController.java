package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.monitor.server.entity.MonitorSysGenRemark;
import com.chua.starter.monitor.server.service.MonitorSysGenRemarkService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 备注控制器
 *
 * @author CH
 * @since 2023/10/18
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "字段备注接口")
@RequestMapping("v1/remark")
public class RemarkController {
    
    @Resource
    private MonitorSysGenRemarkService sysGenRemarkService;


    /**
     * 查询页面
     * 查询列表
     *
     * @param pageNum  书籍页码
     * @param pageSize 页面大小
     * @return {@link ReturnPageResult}<{@link SysGenBackup}>
     */
    @GetMapping("/page")
    public ReturnPageResult<MonitorSysGenRemark> queryPage(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "genId") Integer genId,
                                                    @RequestParam(value = "database", required = false) String database,
                                                    @RequestParam(value = "table", required = false) String table
                                                    ) {
        return PageResultUtils.ok(sysGenRemarkService.page(new Page<MonitorSysGenRemark>(pageNum, pageSize), Wrappers.<MonitorSysGenRemark>lambdaQuery()
                .eq(MonitorSysGenRemark::getGenId, genId)
                .eq(StringUtils.isNotEmpty(table), MonitorSysGenRemark::getRemarkTable, table)
                .eq(StringUtils.isNotEmpty(database), MonitorSysGenRemark::getRemarkDatabase, database)
        ));
    }

    /**
     * 获取详细信息
     *
     * @param id 主键
     */
    @GetMapping(value = "/info")
    public ReturnResult<MonitorSysGenRemark> getInfo(Integer id) {
        return ReturnResult.ok(sysGenRemarkService.getOne(Wrappers.<MonitorSysGenRemark>lambdaQuery().eq(MonitorSysGenRemark::getRemarkId, id)));
    }

    /**
     * 新增
     */
    @PostMapping("/save")
    public ReturnResult<MonitorSysGenRemark> save(@Validated(AddGroup.class) @RequestBody MonitorSysGenRemark sysGenRemark) {
        if(null == sysGenRemark.getGenId()) {
            return ReturnResult.illegal("配置不存在");
        }

        MonitorSysGenRemark remark = sysGenRemarkService.getOne(Wrappers.<MonitorSysGenRemark>lambdaQuery()
                .eq(MonitorSysGenRemark::getGenId, sysGenRemark.getGenId())
                .eq(StringUtils.isNotEmpty(sysGenRemark.getRemarkDatabase()), MonitorSysGenRemark::getRemarkDatabase, sysGenRemark.getRemarkDatabase())
                .eq(MonitorSysGenRemark::getRemarkTable, sysGenRemark.getRemarkTable())
                .eq(MonitorSysGenRemark::getRemarkColumn, sysGenRemark.getRemarkColumn())
        );

        if(null == remark) {
            sysGenRemarkService.save(sysGenRemark);
        } else {
            sysGenRemarkService.update(sysGenRemark, Wrappers.<MonitorSysGenRemark>lambdaUpdate()
                    .eq(MonitorSysGenRemark::getGenId, sysGenRemark.getGenId())
                    .eq(MonitorSysGenRemark::getRemarkDatabase, sysGenRemark.getRemarkDatabase())
                    .eq(MonitorSysGenRemark::getRemarkTable, sysGenRemark.getRemarkTable())
                    .eq(MonitorSysGenRemark::getRemarkColumn, sysGenRemark.getRemarkColumn()));
        }
        return Result.success(sysGenRemark);
    }

    /**
     * 修改
     */
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody MonitorSysGenRemark sysGenRemark) {
        return ReturnResult.ok(sysGenRemarkService.updateById(sysGenRemark));
    }

    /**
     * 删除
     *
     * @param backupIds 主键串
     */
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(String backupIds) {
        if(null == backupIds) {
            return Result.illegal(false, "数据不存在");
        }
        return Result.success(sysGenRemarkService.removeByIds(Arrays.asList(backupIds.split(","))));
    } 
}

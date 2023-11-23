package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.gen.support.entity.SysGenBackup;
import com.chua.starter.gen.support.entity.SysGenRemark;
import com.chua.starter.gen.support.service.SysGenRemarkService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Date;

/**
 * 备注控制器
 *
 * @author CH
 * @since 2023/10/18
 */
@RestController
@SuppressWarnings("ALL")
@RequestMapping("v1/remark")
public class RemarkController {
    
    @Resource
    private SysGenRemarkService sysGenRemarkService;


    /**
     * 查询页面
     * 查询列表
     *
     * @param pageNum  书籍页码
     * @param pageSize 页面大小
     * @return {@link ReturnPageResult}<{@link SysGenBackup}>
     */
    @GetMapping("/page")
    public ReturnPageResult<SysGenRemark> queryPage(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "genId") Integer genId,
                                                    @RequestParam(value = "database", required = false) String database,
                                                    @RequestParam(value = "table", required = false) String table
                                                    ) {
        return PageResultUtils.ok(sysGenRemarkService.page(new Page<SysGenRemark>(pageNum, pageSize), Wrappers.<SysGenRemark>lambdaQuery()
                .eq(SysGenRemark::getGenId, genId)
                .eq(StringUtils.isNotEmpty(table), SysGenRemark::getRemarkTable, table)
                .eq(StringUtils.isNotEmpty(database), SysGenRemark::getRemarkDatabase, database)
        ));
    }

    /**
     * 获取详细信息
     *
     * @param id 主键
     */
    @GetMapping(value = "/info")
    public ReturnResult<SysGenRemark> getInfo(Integer id) {
        return ReturnResult.ok(sysGenRemarkService.getOne(Wrappers.<SysGenRemark>lambdaQuery().eq(SysGenRemark::getRemarkId, id)));
    }

    /**
     * 新增
     */
    @PostMapping("/save")
    public ReturnResult<SysGenRemark> save(@Validated(AddGroup.class) @RequestBody SysGenRemark sysGenRemark) {
        if(null == sysGenRemark.getGenId()) {
            return ReturnResult.illegal("配置不存在");
        }

        SysGenRemark remark = sysGenRemarkService.getOne(Wrappers.<SysGenRemark>lambdaQuery()
                .eq(SysGenRemark::getGenId, sysGenRemark.getGenId())
                .eq(SysGenRemark::getRemarkDatabase, sysGenRemark.getRemarkDatabase())
                .eq(SysGenRemark::getRemarkTable, sysGenRemark.getRemarkTable())
                .eq(SysGenRemark::getRemarkColumn, sysGenRemark.getRemarkColumn())
        );

        if(null == remark) {
            sysGenRemark.setCreateTime(new Date());
            sysGenRemarkService.save(sysGenRemark);
        } else {
            sysGenRemarkService.update(sysGenRemark, Wrappers.<SysGenRemark>lambdaUpdate()
                    .eq(SysGenRemark::getGenId, sysGenRemark.getGenId())
                    .eq(SysGenRemark::getRemarkDatabase, sysGenRemark.getRemarkDatabase())
                    .eq(SysGenRemark::getRemarkTable, sysGenRemark.getRemarkTable())
                    .eq(SysGenRemark::getRemarkColumn, sysGenRemark.getRemarkColumn()));
        }
        return Result.success(sysGenRemark);
    }

    /**
     * 修改
     */
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody SysGenRemark sysGenRemark) {
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

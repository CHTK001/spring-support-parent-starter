package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.JdbcDriverJarVO;
import com.chua.starter.datasourcehub.support.service.JdbcDriverJarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * JDBC 驱动 jar 库。
 *
 * <p>存在的理由：应用产物里不可能内置全部数据库驱动（ojdbc、mssql-jdbc、
 * 各类国产库驱动体积都在数 MB 且许可各异），而让用户自己往某个目录丢 jar
 * 又不可控。于是把「上传」做成一个受管的窄口子：落盘到应用管理的目录、
 * 按 jar 建独立类加载器、台账记指纹，出问题能一眼看出是哪个 jar。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Tag(name = "数据源中心 - JDBC 驱动 jar", description = "JDBC 驱动 jar 上传与选用")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ms/datasource/jdbc-driver")
public class JdbcDriverJarController {

    /**
     * 驱动 jar 库服务
     */
    private final JdbcDriverJarService driverJarService;

    /**
     * 列出全部已上传的驱动 jar。
     *
     * @return 驱动 jar 列表
     */
    @Operation(summary = "列出 JDBC 驱动 jar")
    @GetMapping
    public ReturnResult<List<JdbcDriverJarVO>> list() {
        return driverJarService.list();
    }

    /**
     * 上传驱动 jar。
     *
     * <p>上传时会**立刻尝试加载一次**驱动类：失败就直接把文件与台账一起回滚，
     * 不给用户留下一个「传上去了、但一连数据源就报 ClassNotFound」的半成品。</p>
     *
     * @param file            驱动 jar 文件
     * @param driverClassName 驱动类全名，如 {@code org.h2.Driver}
     * @param remark          备注，可空
     * @return 落库后的驱动 jar
     */
    @Operation(summary = "上传 JDBC 驱动 jar")
    @PostMapping
    public ReturnResult<JdbcDriverJarVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "driverClassName", required = false) String driverClassName,
            @RequestParam(value = "remark", required = false) String remark) {
        return driverJarService.upload(file, driverClassName, remark);
    }

    /**
     * 删除驱动 jar（仍被数据源引用时会被拒绝）。
     *
     * @param id 驱动 jar ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除 JDBC 驱动 jar")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return driverJarService.delete(id);
    }
}
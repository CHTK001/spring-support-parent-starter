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
 * 驱动 jar 库（JDBC 驱动 + 单 jar 的中间件客户端）。
 *
 * <p>存在的理由：应用产物里不可能内置全部数据库驱动（ojdbc、mssql-jdbc、
 * 各类国产库驱动体积都在数 MB 且许可各异），中间件客户端同理——Kafka、MongoDB
 * 这些客户端的体积与传递依赖各不相同，全部打进单 jar 等于强制每个部署都带上
 * 用不上的几十 MB。于是把「上传」做成一个受管的窄口子：落盘到应用管理的目录、
 * 按 jar 建独立类加载器、台账记指纹，出问题能一眼看出是哪个 jar。</p>
 *
 * <p>两类 jar 共用这条通道，由 {@code kind} 区分：{@code jdbc} 校验入口类是
 * {@code java.sql.Driver}，{@code middleware} 校验是 {@code MiddlewareClientProvider}。
 * 上传的中间件 jar 会立即并入 {@code MiddlewareClientRegistry}，不需要重启。</p>
 *
 * <p>Redis / Nacos 这类宿主自带的中间件走内置实现，不需要上传任何 jar。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Tag(name = "数据源中心 - 驱动 jar", description = "JDBC 驱动与中间件客户端的 jar 上传与选用")
@RestController
@RequiredArgsConstructor
@RequestMapping({"/ms/datasource/jdbc-driver", "/ms/datasource/driver-jar"})
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
    @Operation(summary = "列出已上传的驱动 jar（含中间件客户端）")
    @GetMapping
    public ReturnResult<List<JdbcDriverJarVO>> list() {
        return driverJarService.list();
    }

    /**
     * 上传驱动 jar。
     *
     * <p>上传时会**立刻尝试加载一次**入口类：失败就直接把文件回滚，
     * 不给用户留下一个「传上去了、但一用就报 ClassNotFound」的半成品。</p>
     *
     * @param file            驱动 jar 文件
     * @param driverClassName 入口类全名：JDBC 驱动类（如 {@code org.h2.Driver}）
     *                        或中间件 SPI 实现类
     * @param remark          备注，可空
     * @param kind            {@code jdbc}（缺省）或 {@code middleware}
     * @return 落库后的驱动 jar
     */
    @Operation(summary = "上传驱动 jar")
    @PostMapping
    public ReturnResult<JdbcDriverJarVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "driverClassName", required = false) String driverClassName,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "kind", required = false) String kind) {
        return driverJarService.upload(file, driverClassName, remark, kind);
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
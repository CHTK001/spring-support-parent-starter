package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.model.vo.HostArchiveImportVO;
import com.chua.starter.server.support.model.vo.HostArchiveVO;
import com.chua.starter.server.support.service.HostArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主机资产归档接口（导出 / 导入）。
 *
 * <p>与 {@code GET /ms/server/host/export}（CSV，只给人看）不同，这里的 JSON
 * <b>带登录身份的口令密文</b>，用于整机迁移或环境复制。因此：</p>
 * <ul>
 *   <li>只应挂在需要登录的接口前缀下（随主机管理一起受鉴权保护）；</li>
 *   <li>导入是幂等的：同一份文件重复导入不会产生重复主机 / 身份 / 绑定。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Tag(name = "服务器资产-归档", description = "主机台账 + 身份 + 绑定的 JSON 导出导入")
@Slf4j
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/archive")
@RequiredArgsConstructor
public class HostArchiveController {

    /**
     * 归档服务
     */
    private final HostArchiveService hostArchiveService;

    /**
     * 导出归档（含口令密文）。
     *
     * @return 归档内容
     */
    @Operation(summary = "导出主机资产归档(JSON, 含口令密文)")
    @GetMapping
    public ReturnResult<HostArchiveVO> export() {
        try {
            return ReturnResult.ok(hostArchiveService.export());
        } catch (Exception e) {
            log.warn("[server] 主机资产导出失败: {}", e.getMessage());
            return ReturnResult.error("导出失败: " + e.getMessage());
        }
    }

    /**
     * 导入归档（幂等）。
     *
     * @param archive 归档内容
     * @return 导入结果
     */
    @Operation(summary = "导入主机资产归档(JSON)")
    @PostMapping
    public ReturnResult<HostArchiveImportVO> importArchive(@RequestBody HostArchiveVO archive) {
        try {
            return ReturnResult.ok(hostArchiveService.importArchive(archive));
        } catch (Exception e) {
            log.warn("[server] 主机资产导入失败: {}", e.getMessage());
            return ReturnResult.error("导入失败: " + e.getMessage());
        }
    }
}

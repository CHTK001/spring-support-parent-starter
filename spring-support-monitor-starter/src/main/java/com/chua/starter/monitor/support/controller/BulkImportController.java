package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.BulkImportResultVO;
import com.chua.starter.monitor.support.model.BulkPreviewVO;
import com.chua.starter.monitor.support.model.dto.BulkImportDTO;
import com.chua.starter.monitor.support.service.SyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSV 批量导入（快速模式）REST 接口。
 *
 * <p><b>为什么独立于 {@link SyncTaskController}</b>：批量导入的前端契约
 * （{@code bulkImportCsv}）固定调 {@code POST /ms/monitor/bulkimport}，而
 * {@link SyncTaskController} 类级映射是 {@code /ms/monitor/datasync}——方法级
 * {@code /bulkimport} 会被拼成 {@code /ms/monitor/datasync/bulkimport}，与前端不符。
 * Spring 类级前缀无法在方法内避开，故拆一个薄控制器独占该路径，业务逻辑
 * 仍委托同一个 {@link SyncTaskService#bulkImport}。</p>
 *
 * <p>快速模式与 datasync 逐行引擎分离：文件统一转规范 CSV 后按目标库方言走
 * LOAD/COPY 直灌 SPI（MySQL / PostgreSQL），同一文件可并行灌入多个输出目标。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Tag(name = "监控管理", description = "CSV 批量导入（快速模式）")
@RestController
@RequestMapping("/ms/monitor")
@RequiredArgsConstructor
public class BulkImportController {

    /**
     * 数据同步任务服务（承载 bulkImport 业务逻辑）
     */
    private final SyncTaskService syncTaskService;

    /**
     * CSV 批量导入（快速模式 LOAD 直灌）。
     *
     * @param request 批量导入入参（文件路径 + 输出目标列表）
     * @return 每目标行数/耗时/错误 + 整体汇总
     */
    @Operation(summary = "CSV 批量导入（快速模式 LOAD 直灌）")
    @PostMapping("/bulkimport")
    public ReturnResult<BulkImportResultVO> bulkImport(@RequestBody BulkImportDTO request) {
        return syncTaskService.bulkImport(request);
    }

    /**
     * CSV 批量导入（快速模式）文件预览。
     *
     * @param request 批量导入入参（只用 filePath / delimiter / hasHeader / encoding）
     * @return 表头 + 前若干行 + 数据总行数
     */
    @Operation(summary = "CSV 批量导入文件预览（真实读取）")
    @PostMapping("/bulkimport/preview")
    public ReturnResult<BulkPreviewVO> bulkPreview(@RequestBody BulkImportDTO request) {
        return syncTaskService.bulkPreview(request);
    }
}
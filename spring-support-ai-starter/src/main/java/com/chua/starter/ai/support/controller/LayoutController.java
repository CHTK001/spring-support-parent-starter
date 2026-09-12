package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.starter.ai.support.configuration.layout.LayoutClientManager;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
* AI 版面检测控制器。
*
* <p>提供文档版面分析执行能力（文字/表格/图片等区域检测、结构化解析），
* 全部配置读取自 {@code layout} 分组全局设置，配置变更实时生效。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/layout")
@RequiredArgsConstructor
@Tag(name = "AI 版面检测", description = "文档版面区域检测、结构化解析、模型清单")
public class LayoutController {

    /**
    * 版面检测客户端管理器
     */
    private final LayoutClientManager layoutClientManager;

    /**
    * 检测文档版面区域。
    *
    * @param file 图片文件
    * @return 按区域类型分组的检测框 + 总数 + 耗时
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @RequestParam("file"
     */
    @Permission("admin")
    @Operation(summary = "版面区域检测", description = "检测文档中文字/表格/图片等版面区域，返回按类型分组的检测框")
    @PostMapping("/detect")
    public ReturnResult<?> detect(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            long start = System.currentTimeMillis();
            Map<String, List<PredictRectangle>> regions = layoutClientManager.detect(file.getBytes());
            int total = regions.values().stream().mapToInt(List::size).sum();
            return ReturnResult.ok(Map.of(
                    "regions", regions,
                    "total", total,
                    "elapsedMs", System.currentTimeMillis() - start
            ));
        } catch (Exception e) {
            log.error("[Layout] 版面检测失败", e);
            return ReturnResult.error("版面检测失败: " + e.getMessage());
        }
    }

    /**
    * 解析文档版面为结构化文本（Markdown）。
    *
    * @param file 图片文件
    * @return Markdown 文本
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @RequestParam("file"
     */
    @Permission("admin")
    @Operation(summary = "版面结构化解析", description = "将文档版面解析为 Markdown 结构化文本（表格/公式/阅读顺序）")
    @PostMapping("/parse")
    public ReturnResult<?> parse(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            long start = System.currentTimeMillis();
            String markdown = layoutClientManager.parse(file.getBytes());
            return ReturnResult.ok(Map.of(
                    "markdown", markdown == null ? "" : markdown,
                    "elapsedMs", System.currentTimeMillis() - start
            ));
        } catch (Exception e) {
            log.error("[Layout] 版面解析失败", e);
            return ReturnResult.error("版面解析失败: " + e.getMessage());
        }
    }

    /**
    * 版面模型枚举。
    *
    * @return 可用模型清单
     */
    @Permission("admin")
    @Operation(summary = "版面模型清单", description = "枚举可用的版面分析模型")
    @GetMapping("/models")
    public ReturnResult<?> models() {
        try {
            return ReturnResult.ok(layoutClientManager.listModels());
        } catch (Exception e) {
            log.error("[Layout] 模型清单枚举失败: {}", e.getMessage());
            return ReturnResult.error("模型清单枚举失败: " + e.getMessage());
        }
    }
}

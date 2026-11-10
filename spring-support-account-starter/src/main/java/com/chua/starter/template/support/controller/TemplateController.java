package com.chua.starter.template.support.controller;

import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
import com.chua.starter.template.support.model.TemplateSourceInfo;
import com.chua.starter.template.support.model.TemplateSourceDetail;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.printer.PrinterConfig;
import com.chua.starter.template.support.service.SourceSyncerRegistry;
import com.chua.starter.template.support.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 模板管理 控制器
 *
 * <p>模块前缀由 {@code httpConfig.moduleBaseUrl.template} 决定（默认 {@code /api/template}）；
 * 端点对齐设备模块，分页走 {@code POST/GET /page}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@RestController("accountTemplateController")
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    /**
     * 模板服务
     */
    private final TemplateService templateService;

    /**
     * 来源同步器注册表（消息 / 组卷模块通过 ApplicationRunner 上线，如未生效则无来源数据）
     */
    private final SourceSyncerRegistry syncerRegistry;

    /**
     * 分页查询模板
     *
     * @param query 分页与筛选条件
     * @return 模板分页结果
     */
    @GetMapping("/page")
    public PageResult<TemplateInfo> pageTemplates(TemplatePageQuery query) {
        return templateService.pageTemplates(query);
    }

    /**
     * 模板列表（不分页）
     *
     * @param query 筛选条件
     * @return 模板列表
     */
    @GetMapping("/list")
    public List<TemplateInfo> listTemplates(TemplatePageQuery query) {
        return templateService.listTemplates(query);
    }

    /**
     * 获取模板
     *
     * @param id 模板 标识
     * @return 模板信息实体
     */
    @GetMapping("/{id}")
    public TemplateInfo getTemplate(@PathVariable String id) {
        return templateService.getTemplate(id);
    }

    /**
     * 模板详情（按模板 ID 取信息）
     *
     * <p>回答「拿到这个模板之后需要什么」：自定义模板给出设计器产物；固定模板
     * （三方同步）给出三方模板 ID、归属账号、推送渠道与占位符参数，字段形态对齐
     * MessagePush 发送一条模板消息所需的 MessageRequest（templateId / contentType /
     * content / templateParams）。</p>
     *
     * @param id 模板 标识
     * @return 模板详情
     */
    @GetMapping("/{id}/detail")
    public TemplateSourceDetail templateDetail(@PathVariable String id) {
        return templateService.detail(id);
    }

    /**
     * 获取模板设计器数据
     *
     * @param id 模板 标识
     * @return 设计器数据（info + content + mode）
     */
    @GetMapping("/{id}/designer")
    public TemplateDesignerPayload designerTemplate(@PathVariable String id) {
        return templateService.designer(id);
    }

    /**
     * 新增模板
     *
     * @param request 模板保存请求
     * @return 模板信息实体
     */
    @PostMapping("/add")
    public TemplateInfo addTemplate(@RequestBody TemplateSaveRequest request) {
        return templateService.addTemplate(request);
    }

    /**
     * 更新模板
     *
     * @param id      模板 标识
     * @param request 模板保存请求
     * @return 更新后的模板信息实体
     */
    @PutMapping("/{id}")
    public TemplateInfo updateTemplate(@PathVariable String id, @RequestBody TemplateSaveRequest request) {
        return templateService.updateTemplate(id, request);
    }

    /**
     * 删除模板
     *
     * @param id 模板 标识
     */
    @DeleteMapping("/{id}")
    public void deleteTemplate(@PathVariable String id) {
        templateService.removeTemplate(id);
    }

    /**
     * 打印机列表（应用側 hiprint 连接地址与 Token）
     *
     * @return 打印机列表
     */
    @GetMapping("/printer")
    public List<PrinterConfig> printerList() {
        return templateService.printerList();
    }

    /**
     * 自发现打印机
     *
     * @return 打印机设备名列表
     */
    @GetMapping("/printer/discover")
    public List<String> discoverPrinters() {
        return templateService.discoverPrinters();
    }

    /**
     * 设置默认打印机
     *
     * @param name 打印机设备名
     * @return 是否设置成功
     */
    @PostMapping("/printer/default")
    public Boolean setDefaultPrinter(@RequestParam(required = false) String name) {
        return templateService.setDefaultPrinter(name);
    }

    /**
     * 删除打印机
     *
     * @param name 打印机设备名
     * @return 是否删除成功
     */
    @DeleteMapping("/printer/{name}")
    public Boolean removePrinter(@PathVariable String name) {
        return templateService.removePrinter(name);
    }

    /**
     * 设置打印机参数（包含地址/端口/Token）
     *
     * @param printer 打印机参数
     * @return 打印机参数
     */
    @PostMapping("/printer")
    public PrinterConfig setPrinter(@RequestBody PrinterConfig printer) {
        templateService.setPrinter(printer);
        return printer;
    }

    /**
     * 来源同步：恢复所有来源（message / paper）到统一模板库，返回各来源模板数
     *
     * @return source → 模板数
     */
    @PostMapping("/sync")
    public Map<String, Integer> syncAllSources() {
        return syncerRegistry.sync(null);
    }

    /**
     * 来源同步：恢复指定来源（message / paper）到统一模板库，返回该来源模板数
     *
     * @param source 来源标识：message / paper
     * @return source → 模板数
     */
    @PostMapping("/sync/{source}")
    public Map<String, Integer> syncSource(@PathVariable String source) {
        return syncerRegistry.sync(source);
    }

    /**
     * 来源同步器信息（每个来源的已注册数与库中模板数）
     *
     * @return 来源列表
     */
    @GetMapping("/sync/sources")
    public List<TemplateSourceInfo> syncSources() {
        Map<String, Integer> countMap = syncerRegistry.sourceCountMap();
        List<TemplateSourceInfo> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : countMap.entrySet()) {
            result.add(new TemplateSourceInfo(
                    e.getKey(), e.getValue(),
                    syncerRegistry.get(e.getKey()) != null ? 1L : 0L));
        }
        result.sort(Comparator.comparing(TemplateSourceInfo::getSource));
        return result;
    }
}

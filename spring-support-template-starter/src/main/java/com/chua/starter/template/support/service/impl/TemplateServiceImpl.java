package com.chua.starter.template.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.mapper.TemplateInfoMapper;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
import com.chua.starter.template.support.printer.PrinterConfig;
import com.chua.starter.template.support.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板服务实现
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    private final TemplateInfoMapper templateInfoMapper;

    /**
     * 打印机列表（应用侧 hiprint 调用端点使用），key 为打印机设备名
     */
    private final Map<String, PrinterConfig> printerConfigMap = new ConcurrentHashMap<>();

    public TemplateServiceImpl(TemplateInfoMapper templateInfoMapper) {
        this.templateInfoMapper = templateInfoMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateInfo addTemplate(TemplateSaveRequest request) {
        TemplateInfo template = request.toEntity(null);
        template.setTemplateInfoCreator("-1");
        template.setTemplateInfoModifier("-1");
        template.setTemplateInfoCreatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.insert(template);
        log.info("[Template] 新增模板: id={}, name={}", template.getTemplateInfoId(), template.getTemplateInfoName());
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateInfo updateTemplate(String id, TemplateSaveRequest request) {
        TemplateInfo existing = templateInfoMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        // 名称 / 副标题 / 语言 / 类型 / 状态 由请求覆盖，描述直接写，备注可选
        TemplateInfo template = request.toEntity(id);
        template.setTemplateInfoUpdatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.updateById(template);
        log.info("[Template] 更新模板: id={}, name={}", id, template.getTemplateInfoName());
        return templateInfoMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTemplate(String id) {
        TemplateInfo existing = templateInfoMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        // 软删除，避免已发布模板的存量表被污染
        existing.setTemplateInfoDeleted(1);
        existing.setTemplateInfoUpdatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.updateById(existing);
        log.info("[Template] 删除模板: id={}", id);
    }

    @Override
    public TemplateInfo getTemplate(String id) {
        return templateInfoMapper.selectById(id);
    }

    @Override
    public PageResult<TemplateInfo> pageTemplates(TemplatePageQuery query) {
        TemplatePageQuery q = query == null ? new TemplatePageQuery() : query.normalize();
        Page<TemplateInfo> page = new Page<>(q.getPage(), q.getSize());
        LambdaQueryWrapper<TemplateInfo> wrapper = queryWrapper(q);
        IPage<TemplateInfo> iPage = templateInfoMapper.selectPage(page, wrapper);
        return PageResult.copy(iPage);
    }

    @Override
    public List<TemplateInfo> listTemplates(TemplatePageQuery query) {
        LambdaQueryWrapper<TemplateInfo> wrapper = queryWrapper(query == null ? new TemplatePageQuery() : query);
        wrapper.last("LIMIT 500");
        return templateInfoMapper.selectList(wrapper);
    }

    @Override
    public TemplateDesignerPayload designer(String id) {
        TemplateInfo template = templateInfoMapper.selectById(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        TemplateDesignerPayload payload = new TemplateDesignerPayload();
        payload.setId(id);
        payload.setInfo(template);
        payload.setContent(template.getTemplateInfoContent());
        payload.setMode(TemplateDesignerPayload.Mode.design);
        return payload;
    }

    @Override
    public List<PrinterConfig> printerList() {
        return new ArrayList<>(printerConfigMap.values());
    }

    @Override
    public List<String> discoverPrinters() {
        // 实际（局域网 / 本机）打印机发现由应用层或 hiprint 自带 Agent 完成，
        // 这里只返回已配置的打印机，避免未接入时应用侧一直提示“未找到打印机”。
        List<String> names = new ArrayList<>();
        for (PrinterConfig config : printerConfigMap.values()) {
            if (config.getIsDefault()) {
                names.add(0, config.getName());
            } else {
                names.add(config.getName());
            }
        }
        log.info("[Template] 发现打印机: {}", names);
        return names;
    }

    @Override
    public boolean setDefaultPrinter(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        for (PrinterConfig config : printerConfigMap.values()) {
            config.setIsDefault(Boolean.FALSE);
        }
        PrinterConfig config = printerConfigMap.get(name);
        if (config == null) {
            config = new PrinterConfig();
            config.setName(name);
            printerConfigMap.put(name, config);
        }
        config.setIsDefault(Boolean.TRUE);
        log.info("[Template] 设置默认打印机: {}", name);
        return true;
    }

    @Override
    public boolean removePrinter(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        PrinterConfig config = printerConfigMap.remove(name);
        if (config == null) {
            log.warn("[Template] 打印机 {} 不存在", name);
            return false;
        }
        // 被删除的如果是默认打印机，下掉默认标记
        for (PrinterConfig other : printerConfigMap.values()) {
            other.setIsDefault(Boolean.FALSE);
        }
        log.info("[Template] 删除打印机: {}", name);
        return true;
    }

    @Override
    public void setPrinter(PrinterConfig printer) {
        // 添加打印机连接参数（地址 / 端口 / Token），并自动设为默认
        if (printer == null) {
            return;
        }
        printerConfigMap.put(printer.getName(), printer);
        setDefaultPrinter(printer.getName());
        log.info("[Template] 设置打印机: {}", printer.getName());
    }

    /**
     * 构建模板查询条件（与 XML 的查询条件保持同一条 SQL 路径）
     *
     * @param q 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TemplateInfo> queryWrapper(TemplatePageQuery q) {
        LambdaQueryWrapper<TemplateInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateInfo::getTemplateInfoDeleted, 0);
        // 关键字走 name / subtitle / remark 模糊匹配
        if (StringUtils.hasText(q.getKeyword())) {
            wrapper.and(w -> w
                    .like(TemplateInfo::getTemplateInfoName, q.getKeyword())
                    .or().like(TemplateInfo::getTemplateInfoSubtitle, q.getKeyword())
                    .or().like(TemplateInfo::getTemplateInfoRemark, q.getKeyword()));
        }
        // 语言
        if (StringUtils.hasText(q.getLanguage())) {
            wrapper.eq(TemplateInfo::getTemplateInfoLanguage, q.getLanguage());
        }
        // 类型
        if (StringUtils.hasText(q.getType())) {
            wrapper.eq(TemplateInfo::getTemplateInfoType, q.getType());
        }
        // 状态
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(TemplateInfo::getTemplateInfoStatus, q.getStatus());
        }
        // 排序
        SFunction<TemplateInfo, ?> field = getSortByKey(q.getSortBy());
        if (field == null) {
            field = TemplateInfo::getTemplateInfoUpdatedAt;
        }
        if ("asc".equalsIgnoreCase(q.getSortOrder())) {
            wrapper.orderByAsc(field);
        } else {
            wrapper.orderByDesc(field);
        }
        return wrapper;
    }

    /**
     * 排序字段映射
     *
     * @param sortBy 排序字段
     * @return 排序字段
     */
    private static SFunction<TemplateInfo, ?> getSortByKey(String sortBy) {
        switch (sortBy == null ? null : sortBy.trim()) {
            case "name":
                return TemplateInfo::getTemplateInfoName;
            case "createdAt":
                return TemplateInfo::getTemplateInfoCreatedAt;
            default:
                return null;
        }
    }
}

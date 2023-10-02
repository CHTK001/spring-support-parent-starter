package com.chua.starter.gen.support.service.impl;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.mapper.SysGenTableMapper;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.query.Download;
import com.chua.starter.gen.support.result.TemplateResult;
import com.chua.starter.gen.support.service.SysGenColumnService;
import com.chua.starter.gen.support.service.SysGenTableService;
import com.chua.starter.gen.support.util.VelocityInitializer;
import com.chua.starter.gen.support.util.VelocityUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 *    
 * @author CH
 */     
@Service
public class SysGenTableServiceImpl extends ServiceImpl<SysGenTableMapper, SysGenTable> implements SysGenTableService{
    private final IdentifierGenerator identifierGenerator = new DefaultIdentifierGenerator(NetUtils.getLocalAddress());
    @Resource
    private SysGenColumnService sysGenColumnService;

    @Resource
    private GenProperties genProperties;

    @Override
    public byte[] downloadCode(Download download) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        generatorCode(download.getTabIds(), zip, download);
        IoUtils.closeQuietly(zip);
        return outputStream.toByteArray();
    }

    @Override
    public List<TemplateResult> template(Integer tabId) {
        // 获取模板列表
        List<String> templates = Optional.of(VelocityUtils.getTemplateList(genProperties.getTemplatePath())).orElse(Collections.emptyList());
        List<TemplateResult> temp = new LinkedList<>();
        SysGenTable sysGenTable = baseMapper.selectById(tabId);
        List<SysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId));

        Download download = new Download();
        for (String template : templates) {
            temp.add(createTemplateResult(template, sysGenTable, sysGenColumns, download));
        }
        String tabTplCategory = sysGenTable.getTabTplCategory();
        if (StringUtils.isNotEmpty(tabTplCategory)) {
            Set<String> strings = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(tabTplCategory);
            for (String template : strings) {
                temp.add(createTemplateResult(template, sysGenTable, sysGenColumns, download));
            }
        }

        return temp;
    }

    /**
     * 创建模板结果
     *
     * @param template      样板
     * @param sysGenTable   sys-gen表
     * @param sysGenColumns sys gen柱
     * @param download      下载
     * @return {@link TemplateResult}
     */
    private TemplateResult createTemplateResult(String template, SysGenTable sysGenTable, List<SysGenColumn> sysGenColumns, Download download) {
        TemplateResult item = new TemplateResult();
        String fileName = FileUtils.getBaseName(template);
        VelocityInitializer.initVelocity();
        // 设置主键列信息
        setPkColumn(sysGenTable, sysGenColumns);
        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns, download);
        item.setName(fileName);
        item.setType(FileUtils.getExtension(fileName));
        try (StringWriter sw = new StringWriter()) {
            Template tpl = Velocity.getTemplate(template, UTF_8);
            tpl.merge(context, sw);
            item.setContent(sw.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return item;
    }

    /**
     * 生成器代码
     *
     * @param tabId    tabId
     * @param zip      zip
     * @param download download
     */
    private void generatorCode(String tabId, ZipOutputStream zip, Download download) {
        // 查询表信息
        SysGenTable sysGenTable = baseMapper.selectById(tabId);
        List<SysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId));
        VelocityInitializer.initVelocity();
        // 设置主键列信息
        setPkColumn(sysGenTable, sysGenColumns);

        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns, download);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(genProperties.getTemplatePath());
        for (String template : templates) {
            String fileName = VelocityUtils.getFileName(template, sysGenTable, download);
            if(StringUtils.isEmpty(fileName)) {
                continue;
            }
            // 渲染模板
            try {
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(template, UTF_8);
                tpl.merge(context, sw);
                // 添加到zip
                zip.putNextEntry(new ZipEntry(fileName));
                IoUtils.write(zip, StandardCharsets.UTF_8, false, sw.toString());
                IoUtils.closeQuietly(sw);
                zip.flush();
                zip.closeEntry();
            } catch (IOException e) {
                log.error("渲染模板失败，表名：" + sysGenTable.getTabName(), e);
            }
        }
    }

    /**
     * 设置pk列
     * 设置主键列信息
     *
     * @param table         业务表信息
     * @param sysGenColumns sys gen柱
     */
    public void setPkColumn(SysGenTable table, List<SysGenColumn> sysGenColumns) {
        for (SysGenColumn sysGenColumn : sysGenColumns) {
            if("1".equals(sysGenColumn.getColIsPk())) {
                table.setTabPkColumn(sysGenColumn);
            }
        }

        if(null == table.getTabPkColumn()) {
            table.setTabPkColumn(CollectionUtils.findFirst(sysGenColumns));
        }
    }



}

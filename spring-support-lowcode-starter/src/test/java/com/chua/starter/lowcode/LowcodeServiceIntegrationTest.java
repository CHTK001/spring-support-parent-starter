package com.chua.starter.lowcode;

import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.entity.LowFormPage;
import com.chua.starter.lowcode.service.LowFormDirectoryService;
import com.chua.starter.lowcode.service.LowFormService;
import com.chua.starter.lowcode.service.LowPageService;
import com.chua.starter.lowcode.vo.LowFormDirectoryVO;
import com.chua.starter.lowcode.vo.LowFormVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 低代码模块集成测试：真起 Spring 上下文，H2(MySQL 模式) 回放本模块 DDL，
 * 验证「编码唯一 → 发布出快照 → 草稿不外泄 → 重发不撞号 → 回滚」整条链路。
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootTest(classes = LowcodeTestApplication.class)
class LowcodeServiceIntegrationTest {

    @Autowired
    private LowFormService formService;

    @Autowired
    private LowPageService pageService;

    @Autowired
    private LowFormDirectoryService directoryService;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void initSchema() throws Exception {
        // 显式回放本模块的建表脚本：验证 DDL 本身在 MySQL 语法下可执行
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
        }
        // 每个用例独立数据：清表而非依赖内存库重建
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__initdata_lowcode.sql"));
        }
        formService.remove(com.baomidou.mybatisplus.core.toolkit.Wrappers.<LowForm>lambdaQuery()
                .ne(LowForm::getLowFormId, -1));
        pageService.remove(com.baomidou.mybatisplus.core.toolkit.Wrappers.<LowFormPage>lambdaQuery()
                .ne(LowFormPage::getLowPageId, -1));
        directoryService.remove(com.baomidou.mybatisplus.core.toolkit.Wrappers.<LowFormDirectory>lambdaQuery()
                .ne(LowFormDirectory::getLowFormDirectoryId, 1));
    }

    private LowForm newForm(String code) {
        LowForm form = new LowForm();
        form.setLowFormCode(code);
        form.setLowFormName("表单-" + code);
        form.setLowFormSchema("{\"fields\":[]}");
        return form;
    }

    @Test
    @DisplayName("根目录种子存在，目录树包含根节点")
    void seedRootDirectoryExists() {
        List<LowFormDirectoryVO> tree = directoryService.tree();
        assertFalse(tree.isEmpty(), "目录树不应为空");
        assertEquals(1, tree.get(0).getLowFormDirectoryId(), "种子根目录 id 应为 1");
        assertEquals("默认目录", tree.get(0).getLowFormDirectoryName());
        assertEquals(1, directoryService.options().size());
    }

    @Test
    @DisplayName("表单编码唯一由后端强制：重复编码必须被拒绝")
    void duplicateFormCodeRejected() {
        LowForm saved = formService.saveForm(newForm("dup_code"));
        assertNotNull(saved.getLowFormId(), "内部 formId 应自动生成");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> formService.saveForm(newForm("dup_code")));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("未发布时运行期接口取不到 Schema")
    void unpublishedSchemaNotVisible() {
        formService.saveForm(newForm("unpub"));
        assertNull(formService.publishedSchema("unpub"), "未发布不应对外可见");
    }

    @Test
    @DisplayName("发布：当前版本 1，产出 1 条版本快照，运行期按快照取数")
    void publishCreatesSnapshot() {
        LowForm form = formService.saveForm(newForm("pub1"));
        assertTrue(formService.publishForm(form.getLowFormId()));

        LowForm reloaded = formService.getById(form.getLowFormId());
        assertEquals(1, reloaded.getLowFormVersion(), "首次发布版本号应为 1");
        assertEquals(1, reloaded.getLowFormPublishStatus(), "应置为已发布");
        assertEquals(1, reloaded.getLowFormStatus(), "发布后应启用");

        List<LowFormVersionVO> versions = formService.versions(form.getLowFormId());
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getLowFormVersionNo());
        assertEquals("{\"fields\":[]}", formService.publishedSchema("pub1"));
    }

    @Test
    @DisplayName("已发布后改草稿不外泄：运行期仍拿到上次发布快照")
    void draftDoesNotLeakToRuntime() {
        LowForm form = formService.saveForm(newForm("draft_guard"));
        formService.publishForm(form.getLowFormId());

        // 设计器改草稿但不发布
        LowForm patch = new LowForm();
        patch.setLowFormId(form.getLowFormId());
        patch.setLowFormSchema("{\"fields\":[\"draft-only\"]}");
        formService.updateForm(patch);

        assertEquals("{\"fields\":[]}", formService.publishedSchema("draft_guard"),
                "草稿不得直接透给运行期");
    }

    @Test
    @DisplayName("重复发布：版本号按历史递增，不因回滚撞号")
    void republishIncrementsVersion() {
        LowForm form = formService.saveForm(newForm("multi_pub"));
        formService.publishForm(form.getLowFormId());

        // 第二次发布（含回滚后重发场景）
        formService.updateForm(schemaPatch(form.getLowFormId(), "{\"v\":2}"));
        formService.publishForm(form.getLowFormId());

        List<LowFormVersionVO> versions = formService.versions(form.getLowFormId());
        assertEquals(2, versions.size());
        // 降序返回：最新为 2
        assertEquals(2, versions.get(0).getLowFormVersionNo(), "不应出现重复版本号");
        assertEquals(1, versions.get(1).getLowFormVersionNo());
        assertEquals(2, formService.getById(form.getLowFormId()).getLowFormVersion());
    }

    @Test
    @DisplayName("回滚：Schema 与版本号回到目标版本，运行期取回该版本快照")
    void rollbackRestoresVersion() {
        LowForm form = formService.saveForm(newForm("rollback_case"));
        formService.publishForm(form.getLowFormId());                       // v1
        formService.updateForm(schemaPatch(form.getLowFormId(), "{\"v\":2}"));
        formService.publishForm(form.getLowFormId());                       // v2

        LowFormVersionVO v1 = formService.versions(form.getLowFormId()).stream()
                .filter(v -> v.getLowFormVersionNo() == 1).findFirst().orElseThrow();

        assertTrue(formService.rollbackToVersion(v1.getLowFormVersionId()));

        LowForm reloaded = formService.getById(form.getLowFormId());
        assertEquals("{\"fields\":[]}", reloaded.getLowFormSchema(), "Schema 应回滚到 v1");
        assertEquals(1, reloaded.getLowFormVersion(), "版本号应回到 1");
        assertEquals("{\"fields\":[]}", formService.publishedSchema("rollback_case"));

        // 回滚后重发不能撞出重复版本号
        formService.publishForm(form.getLowFormId());
        List<LowFormVersionVO> versions = formService.versions(form.getLowFormId());
        long distinct = versions.stream().map(LowFormVersionVO::getLowFormVersionNo).distinct().count();
        assertEquals(versions.size(), distinct, "版本号必须唯一");
    }

    @Test
    @DisplayName("无 Schema 不允许发布")
    void publishWithoutSchemaRejected() {
        LowForm form = newForm("no_schema");
        form.setLowFormSchema("  ");
        LowForm saved = formService.saveForm(form);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> formService.publishForm(saved.getLowFormId()));
        assertTrue(ex.getMessage().contains("Schema"));
    }

    @Test
    @DisplayName("页面同样支持发布/回滚，且编码唯一")
    void pageLifecycle() {
        LowFormPage page = new LowFormPage();
        page.setLowPageCode("p_home");
        page.setLowPageName("首页");
        page.setLowPageSchema("{\"nodes\":[]}");
        LowFormPage saved = pageService.savePage(page);
        assertNotNull(saved.getLowPageId());

        LowFormPage duplicate = new LowFormPage();
        duplicate.setLowPageCode("p_home");
        duplicate.setLowPageName("重复编码");
        duplicate.setLowPageSchema("{}");
        assertThrows(IllegalArgumentException.class, () -> pageService.savePage(duplicate));

        assertTrue(pageService.publishPage(saved.getLowPageId()));
        assertEquals(1, pageService.getById(saved.getLowPageId()).getLowPageVersion());
        assertEquals("{\"nodes\":[]}", pageService.publishedSchema("p_home"));
    }

    @Test
    @DisplayName("重命名目录不改父级，子树路径链保持正确")
    void renameDirectoryKeepsHierarchy() {
        LowFormDirectory parent = new LowFormDirectory();
        parent.setLowFormDirectoryName("父");
        LowFormDirectory savedParent = directoryService.addDirectory(parent);

        LowFormDirectory child = new LowFormDirectory();
        child.setLowFormDirectoryName("子");
        child.setLowFormDirectoryPid(savedParent.getLowFormDirectoryId());
        LowFormDirectory savedChild = directoryService.addDirectory(child);
        String beforeTreeId = savedChild.getLowFormDirectoryTreeId();
        assertNotNull(beforeTreeId);

        // 只传 id + 名称（前端重命名的最小载荷）
        LowFormDirectory rename = new LowFormDirectory();
        rename.setLowFormDirectoryId(savedChild.getLowFormDirectoryId());
        rename.setLowFormDirectoryName("子-改名");
        assertTrue(directoryService.editDirectory(rename));

        LowFormDirectory after = directoryService.getById(savedChild.getLowFormDirectoryId());
        assertEquals(savedParent.getLowFormDirectoryId(), after.getLowFormDirectoryPid(),
                "未传 pid 时不得把目录挂到根");
        assertEquals(beforeTreeId, after.getLowFormDirectoryTreeId(),
                "父级未变时路径链不得被重置");
        assertEquals("子-改名", after.getLowFormDirectoryName());
    }

    @Test
    @DisplayName("目录存在子目录时不允许删除")
    void deleteDirectoryWithChildrenRejected() {
        LowFormDirectory parent = new LowFormDirectory();
        parent.setLowFormDirectoryName("有子的父");
        LowFormDirectory savedParent = directoryService.addDirectory(parent);

        LowFormDirectory child = new LowFormDirectory();
        child.setLowFormDirectoryName("子");
        child.setLowFormDirectoryPid(savedParent.getLowFormDirectoryId());
        directoryService.addDirectory(child);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> directoryService.deleteDirectory(savedParent.getLowFormDirectoryId()));
        assertTrue(ex.getMessage().contains("子目录"));
    }

    @Test
    @DisplayName("目录下还有表单时不允许删除，避免孤儿记录")
    void deleteDirectoryWithFormsRejected() {
        LowFormDirectory dir = new LowFormDirectory();
        dir.setLowFormDirectoryName("有表单");
        LowFormDirectory saved = directoryService.addDirectory(dir);

        LowForm form = newForm("in_dir");
        form.setLowFormDirectoryId(saved.getLowFormDirectoryId());
        formService.saveForm(form);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> directoryService.deleteDirectory(saved.getLowFormDirectoryId()));
        assertTrue(ex.getMessage().contains("表单"));
    }

    @Test
    @DisplayName("回环防护：父目录不能挂到自己的子孙下")
    void cyclicParentRejected() {
        LowFormDirectory a = new LowFormDirectory();
        a.setLowFormDirectoryName("A");
        LowFormDirectory savedA = directoryService.addDirectory(a);

        LowFormDirectory b = new LowFormDirectory();
        b.setLowFormDirectoryName("B");
        b.setLowFormDirectoryPid(savedA.getLowFormDirectoryId());
        LowFormDirectory savedB = directoryService.addDirectory(b);

        LowFormDirectory moveA = new LowFormDirectory();
        moveA.setLowFormDirectoryId(savedA.getLowFormDirectoryId());
        moveA.setLowFormDirectoryName("A");
        moveA.setLowFormDirectoryPid(savedB.getLowFormDirectoryId());

        assertThrows(IllegalArgumentException.class, () -> directoryService.editDirectory(moveA));
    }

    private LowForm schemaPatch(Integer formId, String schema) {
        LowForm patch = new LowForm();
        patch.setLowFormId(formId);
        patch.setLowFormCode(formService.getById(formId).getLowFormCode());
        patch.setLowFormSchema(schema);
        return patch;
    }
}

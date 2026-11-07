package com.chua.starter.lowcode;

import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.service.LowFormService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审计字段探针：验证 SysBase 的创建/更新审计列是否真的落库。
 *
 * <p>这类缺陷编译期完全看不出来：MetaObjectHandler 没注册时列就是 NULL，
 * 只有真正写一次库并回读才能发现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootTest(classes = {LowcodeTestApplication.class, StubAuthConfiguration.class})
class AuditFieldProbeTest {

    @Autowired
    private LowFormService formService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StubAuthConfiguration.StubAuthService stub;

    @Test
    void timestampsFilledWithoutUser() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
        }
        stub.setUser(null);

        LowForm form = newForm("audit_ts");
        LowForm saved = formService.saveForm(form);
        assertNotNull(formService.getById(saved.getLowFormId()).getCreateTime(),
                "create_time 必须自动填充");

        LowForm patch = new LowForm();
        patch.setLowFormId(saved.getLowFormId());
        patch.setLowFormSchema("{\"v\":2}");
        formService.updateForm(patch);

        LowForm reloaded = formService.getById(saved.getLowFormId());
        assertNotNull(reloaded.getCreateTime(), "create_time 必须保留");
        assertNotNull(reloaded.getUpdateTime(), "update_time 必须自动填充");
    }

    @Test
    void userColumnsFilledOnInsertAndUpdate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
        }

        stub.setUser(StubAuthConfiguration.StubAuthService.user("张三", "42"));
        try {
            LowForm saved = formService.saveForm(newForm("audit_user"));

            LowForm reloaded = formService.getById(saved.getLowFormId());
            assertEquals("张三", reloaded.getCreateName(), "create_name 应写入登录用户名");
            assertEquals(42, reloaded.getCreateBy(), "create_by 应写入登录用户ID");

            // 更新阶段换成另一个用户，验证 update_* 独立填充
            stub.setUser(StubAuthConfiguration.StubAuthService.user("李四", "7"));
            LowForm patch = new LowForm();
            patch.setLowFormId(saved.getLowFormId());
            patch.setLowFormSchema("{\"v\":3}");
            formService.updateForm(patch);

            LowForm updated = formService.getById(saved.getLowFormId());
            assertEquals("李四", updated.getUpdateName(), "update_name 应写入当前登录用户名");
            assertEquals(7, updated.getUpdateBy(), "update_by 应写入当前登录用户ID");
            assertEquals("张三", updated.getCreateName(), "create_name 不应被更新覆盖");
            assertEquals(42, updated.getCreateBy(), "create_by 不应被更新覆盖");
        } finally {
            stub.setUser(null);
        }
    }

    /**
     * 直接查库核对，避免只信实体回读。
     *
     * @param id 主键
     * @return 一行审计列
     * @throws Exception 查询失败
     */
    private String dumpAudit(Integer id) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "select create_name, create_by, create_time, update_name, update_by, update_time "
                            + "from low_form where low_form_id = " + id)) {
                if (!rs.next()) {
                    return "<no row>";
                }
                return "create_name=" + rs.getString(1)
                        + " create_by=" + rs.getObject(2)
                        + " create_time=" + rs.getObject(3)
                        + " update_name=" + rs.getString(4)
                        + " update_by=" + rs.getObject(5)
                        + " update_time=" + rs.getObject(6);
            }
        }
    }

    /**
     * 构造一个最小可用表单。
     *
     * @param code 编码
     * @return 表单
     */
    private LowForm newForm(String code) {
        LowForm form = new LowForm();
        form.setLowFormCode(code + "_" + System.nanoTime());
        form.setLowFormName("审计探针");
        form.setLowFormSchema("{}");
        return form;
    }
}

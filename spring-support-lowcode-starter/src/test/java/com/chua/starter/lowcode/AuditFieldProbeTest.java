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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审计字段探针：验证 SysBase 的创建/更新审计列是否真的落库。
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootTest(classes = LowcodeTestApplication.class)
class AuditFieldProbeTest {

    @Autowired
    private LowFormService formService;

    @Autowired
    private DataSource dataSource;

    @Test
    void probeAuditColumns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
        }

        LowForm form = new LowForm();
        form.setLowFormCode("audit_probe_" + System.nanoTime());
        form.setLowFormName("审计探针");
        form.setLowFormSchema("{}");
        LowForm saved = formService.saveForm(form);

        dump("INSERT 后", saved.getLowFormId());

        LowForm patch = new LowForm();
        patch.setLowFormId(saved.getLowFormId());
        patch.setLowFormSchema("{\"v\":2}");
        formService.updateForm(patch);

        dump("UPDATE 后", saved.getLowFormId());

        LowForm reloaded = formService.getById(saved.getLowFormId());
        assertNotNull(reloaded.getCreateTime(), "create_time 应自动填充");
        System.out.println("createTime=" + reloaded.getCreateTime());
        System.out.println("updateTime=" + reloaded.getUpdateTime());
        System.out.println("createName=" + reloaded.getCreateName());
        System.out.println("updateName=" + reloaded.getUpdateName());
    }

    private void dump(String stage, Integer id) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "select create_name, create_by, create_time, update_name, update_by, update_time "
                            + "from low_form where low_form_id = " + id)) {
                if (rs.next()) {
                    System.out.println("[" + stage + "] create_name=" + rs.getString(1)
                            + " create_by=" + rs.getObject(2)
                            + " create_time=" + rs.getObject(3)
                            + " update_name=" + rs.getString(4)
                            + " update_by=" + rs.getObject(5)
                            + " update_time=" + rs.getObject(6));
                }
            }
        }
    }
}

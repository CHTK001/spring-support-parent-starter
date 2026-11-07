package com.chua.starter.lowcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 低代码 HTTP 层测试：验证 /v2/lowcode/** 的路由、参数绑定与 ReturnResult 包装。
 *
 * <p>Service 层单测无法覆盖「路径写错、参数名对不上、返回包装没生效」这类问题，
 * 这里用真实 MockMvc 走一遍完整请求链。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootTest(classes = {LowcodeTestApplication.class, StubAuthConfiguration.class})
@AutoConfigureMockMvc
class LowcodeHttpApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void initSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__initdata_lowcode.sql"));
        }
    }

    @Test
    @DisplayName("GET /v2/lowcode/directory/tree 返回种子根目录")
    void directoryTree() throws Exception {
        mockMvc.perform(get("/v2/lowcode/directory/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.ok").value(true))
                // 上下文在用例间共享，不能断言「第一个就是根」，只断言根目录存在且名字正确
                .andExpect(jsonPath("$.data[?(@.lowFormDirectoryId == 1)].lowFormDirectoryName")
                        .value("默认目录"));
    }

    @Test
    @DisplayName("GET /v2/lowcode/directory/options 返回下拉项")
    void directoryOptions() throws Exception {
        mockMvc.perform(get("/v2/lowcode/directory/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[?(@.value == '1')].label").value("默认目录"));
    }

    @Test
    @DisplayName("POST /save 走通，再用 GET /code/{formCode} 回读")
    void saveAndQueryByCode() throws Exception {
        String body = "{\"lowFormCode\":\"http_form_1\",\"lowFormName\":\"HTTP表单\",\"lowFormSchema\":\"{\\\"a\\\":1}\"}";

        mockMvc.perform(post("/v2/lowcode/form/save")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.lowFormId").isNumber())
                .andExpect(jsonPath("$.data.lowFormCode").value("http_form_1"));

        mockMvc.perform(get("/v2/lowcode/form/code/http_form_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowFormName").value("HTTP表单"));
    }

    @Test
    @DisplayName("GET /page 分页接口参数绑定正确（Query + 条件对象）")
    void formPageBindsQuery() throws Exception {
        mockMvc.perform(post("/v2/lowcode/form/save")
                        .contentType("application/json")
                        .content("{\"lowFormCode\":\"page_q\",\"lowFormName\":\"分页表单\",\"lowFormSchema\":\"{}\"}"))
                .andExpect(status().isOk());

        // ReturnPageResult 与 ReturnResult 的成功码不同：分页是数值 0，且列表在 data.data
        mockMvc.perform(get("/v2/lowcode/form/page")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data[0].lowFormCode").value("page_q"));
    }

    @Test
    @DisplayName("发布 → 运行期取快照 → 撤销发布后取不到")
    void publishAndUnpublishOverHttp() throws Exception {
        Integer formId = saveForm("http_pub", "{\\\"v\\\":1}");

        mockMvc.perform(post("/v2/lowcode/form/publish").param("formId", String.valueOf(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/v2/lowcode/form/api/http_pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{\"v\":1}"));

        mockMvc.perform(post("/v2/lowcode/form/unpublish").param("formId", String.valueOf(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/v2/lowcode/form/api/http_pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("停用后运行期取不到 Schema，重新启用后恢复")
    void disableHidesFromRuntime() throws Exception {
        Integer formId = saveForm("http_disable", "{\\\"v\\\":2}");
        mockMvc.perform(post("/v2/lowcode/form/publish").param("formId", String.valueOf(formId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v2/lowcode/form/status")
                        .param("formId", String.valueOf(formId)).param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/v2/lowcode/form/api/http_disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/v2/lowcode/form/status")
                        .param("formId", String.valueOf(formId)).param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/v2/lowcode/form/api/http_disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{\"v\":2}"));
    }

    @Test
    @DisplayName("GET /versions 返回版本列表")
    void versions() throws Exception {
        Integer formId = saveForm("http_ver", "{\\\"v\\\":1}");
        mockMvc.perform(post("/v2/lowcode/form/publish").param("formId", String.valueOf(formId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/lowcode/form/versions").param("formId", String.valueOf(formId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lowFormVersionNo").value(1))
                .andExpect(jsonPath("$.data[0].lowFormVersionId").isNumber());
    }

    @Test
    @DisplayName("页面接口：保存 / 发布 / 运行期取数 / 删除")
    void pageApi() throws Exception {
        MvcResult saved = mockMvc.perform(post("/v2/lowcode/page/save")
                        .contentType("application/json")
                        .content("{\"lowPageCode\":\"http_page\",\"lowPageName\":\"HTTP页面\",\"lowPageSchema\":\"{\\\"n\\\":1}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowPageId").isNumber())
                .andReturn();
        Integer pageId = readId(saved, "lowPageId");

        mockMvc.perform(post("/v2/lowcode/page/publish").param("pageId", String.valueOf(pageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/v2/lowcode/page/api/http_page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{\"n\":1}"));

        mockMvc.perform(delete("/v2/lowcode/page/delete").param("pageId", String.valueOf(pageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("目录接口：新增 / 修改 / 删除")
    void directoryCrud() throws Exception {
        MvcResult saved = mockMvc.perform(post("/v2/lowcode/directory/save")
                        .contentType("application/json")
                        .content("{\"lowFormDirectoryName\":\"HTTP目录\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowFormDirectoryId").isNumber())
                .andReturn();
        Integer dirId = readId(saved, "lowFormDirectoryId");

        mockMvc.perform(put("/v2/lowcode/directory/update")
                        .contentType("application/json")
                        .content("{\"lowFormDirectoryId\":" + dirId + ",\"lowFormDirectoryName\":\"HTTP目录-改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(delete("/v2/lowcode/directory/delete").param("directoryId", String.valueOf(dirId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    /**
     * 保存一个表单并返回其主键，避免用硬编码 id 造成测试互相干扰。
     *
     * @param code   编码
     * @param schema Schema（已转义的 JSON 串）
     * @return 表单主键
     * @throws Exception 请求失败
     */
    private Integer saveForm(String code, String schema) throws Exception {
        MvcResult result = mockMvc.perform(post("/v2/lowcode/form/save")
                        .contentType("application/json")
                        .content("{\"lowFormCode\":\"" + code + "\",\"lowFormName\":\"接口表单\","
                                + "\"lowFormSchema\":\"" + schema + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result, "lowFormId");
    }

    /**
     * 从响应体里读主键。
     *
     * @param result 响应
     * @param field  字段名
     * @return 主键
     */
    private Integer readId(MvcResult result, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(result.getResponse().getContentAsString());
            return node.path("data").path(field).asInt();
        } catch (Exception e) {
            throw new IllegalStateException("解析响应失败", e);
        }
    }
}

package com.chua.spring.support.email;

import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.service.EmailAccountImportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 邮箱账户导入导出测试
 * 
 * @author CH
 */
public class EmailImportExportTest {

    private EmailAccountImportExportService service;
    private List<EmailAccount> testAccounts;

    @BeforeEach
    public void setUp() {
        service = new EmailAccountImportExportService();
        testAccounts = createTestAccounts();
    }

    /**
     * 测试导出到 JSON
     */
    @Test
    public void testExportToJson() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.exportToJson(testAccounts, outputStream);

        String json = outputStream.toString();
        assertNotNull(json);
        assertTrue(json.contains("test@example.com"));
        assertTrue(json.contains("version"));
        assertTrue(json.contains("exportTime"));
    }

    /**
     * 测试从 JSON 导入
     */
    @Test
    public void testImportFromJson() throws Exception {
        // 先导出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportToJson(testAccounts, outputStream);

        // 再导入
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        List<EmailAccount> imported = service.importFromJson(inputStream);

        assertNotNull(imported);
        assertEquals(testAccounts.size(), imported.size());
        assertEquals(testAccounts.get(0).getEmailAddress(), imported.get(0).getEmailAddress());
    }

    /**
     * 测试导出到 CSV
     */
    @Test
    public void testExportToCsv() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.exportToCsv(testAccounts, outputStream);

        String csv = outputStream.toString();
        assertNotNull(csv);
        assertTrue(csv.contains("test@example.com"));
        assertTrue(csv.contains("邮箱地址"));
    }

    /**
     * 测试从 CSV 导入
     */
    @Test
    public void testImportFromCsv() throws Exception {
        // 先导出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportToCsv(testAccounts, outputStream);

        // 再导入
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        List<EmailAccount> imported = service.importFromCsv(inputStream);

        assertNotNull(imported);
        assertEquals(testAccounts.size(), imported.size());
        assertEquals(testAccounts.get(0).getEmailAddress(), imported.get(0).getEmailAddress());
    }

    /**
     * 测试密码加密解密
     */
    @Test
    public void testPasswordEncryption() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportToJson(testAccounts, outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        List<EmailAccount> imported = service.importFromJson(inputStream);

        // 密码应该被正确解密
        assertEquals(testAccounts.get(0).getPassword(), imported.get(0).getPassword());
    }

    /**
     * 测试空列表导出
     */
    @Test
    public void testExportEmptyList() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.exportToJson(new ArrayList<>(), outputStream);

        String json = outputStream.toString();
        assertNotNull(json);
        assertTrue(json.contains("\"accounts\":[]"));
    }

    /**
     * 测试 CSV 特殊字符处理
     */
    @Test
    public void testCsvSpecialCharacters() throws Exception {
        EmailAccount account = new EmailAccount();
        account.setEmailAddress("test@example.com");
        account.setDisplayName("测试,账户"); // 包含逗号
        account.setSmtpHost("smtp.example.com");
        account.setSmtpPort(465);
        account.setImapHost("imap.example.com");
        account.setImapPort(993);
        account.setUsername("test@example.com");
        account.setPassword("password123");
        account.setProtocol("imap");
        account.setSslEnabled(true);
        account.setIsDefault(false);

        List<EmailAccount> accounts = List.of(account);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportToCsv(accounts, outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        List<EmailAccount> imported = service.importFromCsv(inputStream);

        assertEquals("测试,账户", imported.get(0).getDisplayName());
    }

    /**
     * 创建测试账户
     */
    private List<EmailAccount> createTestAccounts() {
        List<EmailAccount> accounts = new ArrayList<>();

        EmailAccount account1 = new EmailAccount();
        account1.setEmailAddress("test@example.com");
        account1.setDisplayName("测试账户");
        account1.setSmtpHost("smtp.example.com");
        account1.setSmtpPort(465);
        account1.setImapHost("imap.example.com");
        account1.setImapPort(993);
        account1.setUsername("test@example.com");
        account1.setPassword("password123");
        account1.setProtocol("imap");
        account1.setSslEnabled(true);
        account1.setIsDefault(false);
        accounts.add(account1);

        EmailAccount account2 = new EmailAccount();
        account2.setEmailAddress("test2@example.com");
        account2.setDisplayName("测试账户2");
        account2.setSmtpHost("smtp.example.com");
        account2.setSmtpPort(465);
        account2.setImapHost("imap.example.com");
        account2.setImapPort(993);
        account2.setUsername("test2@example.com");
        account2.setPassword("password456");
        account2.setProtocol("imap");
        account2.setSslEnabled(true);
        account2.setIsDefault(true);
        accounts.add(account2);

        return accounts;
    }
}

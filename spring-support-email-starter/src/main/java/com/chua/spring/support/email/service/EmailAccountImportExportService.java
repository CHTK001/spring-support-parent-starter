package com.chua.spring.support.email.service;

import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.entity.EmailAccountExport;
import com.chua.spring.support.email.util.PasswordEncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 邮箱账户导入导出服务
 * 
 * @author CH
 */
@Slf4j
@Service
public class EmailAccountImportExportService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导出账户到 JSON
     */
    public void exportToJson(List<EmailAccount> accounts, OutputStream outputStream) {
        try {
            List<EmailAccount> safeAccounts = accounts == null ? List.of() : accounts;
            EmailAccountExport export = new EmailAccountExport();
            export.setExportTime(System.currentTimeMillis());

            List<EmailAccountExport.EmailAccountData> accountDataList = safeAccounts.stream()
                    .map(this::convertToExportData)
                    .collect(Collectors.toList());

            export.setAccounts(accountDataList);
            export.setGroups(new ArrayList<>());

            objectMapper.writeValue(outputStream, export);

            log.info("成功导出 {} 个账户", safeAccounts.size());
        } catch (Exception e) {
            log.error("导出账户失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 导入账户
     */
    public List<EmailAccount> importFromJson(InputStream inputStream) {
        try {
            EmailAccountExport export = objectMapper.readValue(inputStream, EmailAccountExport.class);
            List<EmailAccountExport.EmailAccountData> accountDataList = export.getAccounts() == null
                    ? List.of()
                    : export.getAccounts();
            List<EmailAccount> accounts = accountDataList.stream()
                    .map(this::convertFromExportData)
                    .collect(Collectors.toList());

            log.info("成功导入 {} 个账户", accounts.size());
            return accounts;
        } catch (Exception e) {
            log.error("导入账户失败", e);
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    /**
     * 导出账户到 CSV
     */
    public void exportToCsv(List<EmailAccount> accounts, OutputStream outputStream) {
        try {
            StringBuilder csv = new StringBuilder();
            // CSV 头部
            csv.append("邮箱地址,显示名称,SMTP主机,SMTP端口,IMAP主机,IMAP端口,用户名,密码,协议,启用SSL,默认账户\n");

            for (EmailAccount account : accounts) {
                csv.append(escapeCsv(account.getEmailAddress())).append(",");
                csv.append(escapeCsv(account.getDisplayName())).append(",");
                csv.append(escapeCsv(account.getSmtpHost())).append(",");
                csv.append(account.getSmtpPort()).append(",");
                csv.append(escapeCsv(account.getImapHost())).append(",");
                csv.append(account.getImapPort()).append(",");
                csv.append(escapeCsv(account.getUsername())).append(",");
                csv.append(escapeCsv(encryptPassword(account.getPassword()))).append(",");
                csv.append(escapeCsv(account.getProtocol())).append(",");
                csv.append(account.getSslEnabled()).append(",");
                csv.append(account.getIsDefault()).append("\n");
            }

            outputStream.write(csv.toString().getBytes("UTF-8"));
            log.info("成功导出 {} 个账户到 CSV", accounts.size());
        } catch (Exception e) {
            log.error("导出 CSV 失败", e);
            throw new RuntimeException("导出 CSV 失败: " + e.getMessage());
        }
    }

    /**
     * 从 CSV 导入账户
     */
    public List<EmailAccount> importFromCsv(InputStream inputStream) {
        try {
            List<EmailAccount> accounts = new ArrayList<>();
            String content = new String(inputStream.readAllBytes(), "UTF-8");
            String[] lines = content.split("\n");

            // 跳过头部
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty())
                    continue;

                String[] fields = parseCsvLine(line);
                if (fields.length >= 11) {
                    EmailAccount account = new EmailAccount();
                    account.setEmailAddress(fields[0]);
                    account.setDisplayName(fields[1]);
                    account.setSmtpHost(fields[2]);
                    account.setSmtpPort(Integer.parseInt(fields[3]));
                    account.setImapHost(fields[4]);
                    account.setImapPort(Integer.parseInt(fields[5]));
                    account.setUsername(fields[6]);
                    account.setPassword(decryptPassword(fields[7]));
                    account.setProtocol(fields[8]);
                    account.setSslEnabled(Boolean.parseBoolean(fields[9]));
                    account.setIsDefault(Boolean.parseBoolean(fields[10]));
                    accounts.add(account);
                }
            }

            log.info("成功从 CSV 导入 {} 个账户", accounts.size());
            return accounts;
        } catch (Exception e) {
            log.error("导入 CSV 失败", e);
            throw new RuntimeException("导入 CSV 失败: " + e.getMessage());
        }
    }

    private EmailAccountExport.EmailAccountData convertToExportData(EmailAccount account) {
        EmailAccountExport.EmailAccountData data = new EmailAccountExport.EmailAccountData();
        data.setEmailAddress(account.getEmailAddress());
        data.setDisplayName(account.getDisplayName());
        data.setSmtpHost(account.getSmtpHost());
        data.setSmtpPort(account.getSmtpPort());
        data.setImapHost(account.getImapHost());
        data.setImapPort(account.getImapPort());
        data.setUsername(account.getUsername());
        data.setPassword(encryptPassword(account.getPassword()));
        data.setProtocol(account.getProtocol());
        data.setSslEnabled(account.getSslEnabled());
        data.setIsDefault(account.getIsDefault());
        data.setTags(new ArrayList<>());
        return data;
    }

    private EmailAccount convertFromExportData(EmailAccountExport.EmailAccountData data) {
        EmailAccount account = new EmailAccount();
        account.setEmailAddress(data.getEmailAddress());
        account.setDisplayName(data.getDisplayName());
        account.setSmtpHost(data.getSmtpHost());
        account.setSmtpPort(data.getSmtpPort());
        account.setImapHost(data.getImapHost());
        account.setImapPort(data.getImapPort());
        account.setUsername(data.getUsername());
        account.setPassword(decryptPassword(data.getPassword()));
        account.setProtocol(data.getProtocol());
        account.setSslEnabled(data.getSslEnabled());
        account.setIsDefault(data.getIsDefault());
        return account;
    }

    /**
     * 加密密码（使用 AES-256-GCM）
     */
    private String encryptPassword(String password) {
        if (password == null)
            return null;
        return PasswordEncryptionUtil.encrypt(password);
    }

    /**
     * 解密密码（使用 AES-256-GCM）
     */
    private String decryptPassword(String encrypted) {
        if (encrypted == null)
            return null;
        return PasswordEncryptionUtil.decrypt(encrypted);
    }

    /**
     * CSV 字段转义
     */
    private String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 解析 CSV 行
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());

        return fields.toArray(new String[0]);
    }
}

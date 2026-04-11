package com.chua.starter.soft.support.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.soft.support.entity.SoftPackageProfile;
import com.chua.starter.soft.support.entity.SoftPackageProfileField;
import com.chua.starter.soft.support.entity.SoftPackageProfileTemplate;
import com.chua.starter.soft.support.mapper.SoftPackageProfileFieldMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileTemplateMapper;
import com.chua.starter.soft.support.util.SoftJsons;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SoftBuiltinProfileBootstrapper implements ApplicationRunner {

    private final SoftPackageProfileMapper profileMapper;
    private final SoftPackageProfileFieldMapper fieldMapper;
    private final SoftPackageProfileTemplateMapper templateMapper;

    public SoftBuiltinProfileBootstrapper(
            SoftPackageProfileMapper profileMapper,
            SoftPackageProfileFieldMapper fieldMapper,
            SoftPackageProfileTemplateMapper templateMapper
    ) {
        this.profileMapper = profileMapper;
        this.fieldMapper = fieldMapper;
        this.templateMapper = templateMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsertGenericProfile();
        upsertMysqlProfile();
        upsertRedisProfile();
        upsertNginxProfile();
        upsertMinioProfile();
        upsertNacosProfile();
    }

    private void upsertGenericProfile() {
        SoftPackageProfile profile = upsertProfile("generic", "通用软件", "通用软件安装模板", List.of("/logs/${serviceName}.log"), List.of("${installPath}/conf/app.conf"));
        replaceFields(profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "${packageName}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/${packageCode}/${versionCode}", null, null),
                field("serviceName", "服务名称", "service", "input", "服务引导", 30, false, "${packageCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 40, false, "false", null, null),
                field("environmentName", "环境标识", "config", "input", "配置初始化", 50, false, "prod", null, null)
        );
        replaceTemplates(profile,
                template("INSTALL_SCRIPT", "INSTALL_SCRIPT", "安装脚本", null,
                        "mkdir -p ${installPath}\necho profile=${profileCode} > ${installPath}/.profile\n"),
                template("UNINSTALL_SCRIPT", "UNINSTALL_SCRIPT", "卸载脚本", null, "rm -rf ${installPath}"),
                template("CONFIG_TEMPLATE", "app-config", "默认配置", "${installPath}/conf/app.conf",
                        "app.name=${packageName}\napp.env=${environmentName}\n")
        );
    }

    private void upsertMysqlProfile() {
        SoftPackageProfile profile = upsertProfile("mysql", "MySQL", "MySQL 安装画像", List.of("${installPath}/logs/error.log"), List.of("${installPath}/conf/my.cnf"));
        replaceFields(profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "mysql-${versionCode}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/mysql/${versionCode}", null, null),
                field("dataDirectory", "数据目录", "install", "input", "目录与端口", 30, true, "${installPath}/data", null, null),
                field("port", "端口", "install", "number", "目录与端口", 40, true, "3306", Map.of("min", 1, "max", 65535), null),
                field("rootPassword", "Root 密码", "install", "password", "账号凭证", 50, true, "root123456", null, null),
                field("characterSet", "字符集", "config", "select", "配置初始化", 60, false, "utf8mb4", null, List.of(option("utf8mb4", "utf8mb4"), option("utf8", "utf8"))),
                field("serviceName", "服务名称", "service", "input", "服务引导", 70, false, "mysql-${versionCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 80, false, "true", null, null)
        );
        replaceTemplates(profile,
                template("CONFIG_TEMPLATE", "mysql-config", "MySQL 主配置", "${installPath}/conf/my.cnf",
                        "[mysqld]\nport=${port}\nbasedir=${installPath}\ndatadir=${dataDirectory}\ncharacter-set-server=${characterSet}\n"),
                template("INSTALL_SCRIPT", "INSTALL_SCRIPT", "MySQL 安装脚本", null,
                        "mkdir -p ${installPath} ${dataDirectory} ${installPath}/logs\n" +
                                "touch ${installPath}/bin/mysqld ${installPath}/start.sh\n")
        );
    }

    private void upsertRedisProfile() {
        SoftPackageProfile profile = upsertProfile("redis", "Redis", "Redis 安装画像", List.of("${installPath}/logs/redis.log"), List.of("${installPath}/conf/redis.conf"));
        replaceFields(profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "redis-${versionCode}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/redis/${versionCode}", null, null),
                field("dataDirectory", "数据目录", "install", "input", "目录与端口", 30, true, "${installPath}/data", null, null),
                field("port", "端口", "install", "number", "目录与端口", 40, true, "6379", Map.of("min", 1, "max", 65535), null),
                field("password", "密码", "config", "password", "账号凭证", 50, false, "", null, null),
                field("appendonly", "AOF 持久化", "config", "switch", "配置初始化", 60, false, "true", null, null),
                field("serviceName", "服务名称", "service", "input", "服务引导", 70, false, "redis-${versionCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 80, false, "true", null, null)
        );
        replaceTemplates(profile,
                template("CONFIG_TEMPLATE", "redis-config", "Redis 配置", "${installPath}/conf/redis.conf",
                        "port ${port}\ndir ${dataDirectory}\nappendonly ${appendonly}\nrequirepass ${password}\n"),
                template("INSTALL_SCRIPT", "INSTALL_SCRIPT", "Redis 安装脚本", null,
                        "mkdir -p ${installPath} ${dataDirectory} ${installPath}/logs\n" +
                                "touch ${installPath}/bin/redis-server ${installPath}/bin/redis-cli\n")
        );
    }

    private void upsertNginxProfile() {
        SoftPackageProfile profile = upsertProfile("nginx", "Nginx", "Nginx 安装画像", List.of("${installPath}/logs/access.log", "${installPath}/logs/error.log"), List.of("${installPath}/conf/nginx.conf"));
        replaceFields(profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "nginx-${versionCode}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/nginx/${versionCode}", null, null),
                field("httpPort", "HTTP 端口", "config", "number", "目录与端口", 30, true, "80", Map.of("min", 1, "max", 65535), null),
                field("httpsPort", "HTTPS 端口", "config", "number", "目录与端口", 40, false, "443", Map.of("min", 1, "max", 65535), null),
                field("configDirectory", "配置目录", "config", "input", "配置初始化", 50, true, "${installPath}/conf", null, null),
                field("serviceName", "服务名称", "service", "input", "服务引导", 60, false, "nginx-${versionCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 70, false, "true", null, null)
        );
        replaceTemplates(profile,
                template("CONFIG_TEMPLATE", "nginx-config", "Nginx 配置", "${configDirectory}/nginx.conf",
                        "events {}\nhttp {\n  server {\n    listen ${httpPort};\n    location / { root ${installPath}/html; index index.html; }\n  }\n}\n"),
                template("INSTALL_SCRIPT", "INSTALL_SCRIPT", "Nginx 安装脚本", null,
                        "mkdir -p ${installPath}/html ${installPath}/logs ${configDirectory}\n" +
                                "touch ${installPath}/sbin/nginx\n")
        );
    }

    private void upsertMinioProfile() {
        SoftPackageProfile profile = upsertProfile("minio", "MinIO", "MinIO 安装画像", List.of("${installPath}/logs/minio.log"), List.of("${installPath}/conf/minio.env"));
        replaceFields(profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "minio-${versionCode}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/minio/${versionCode}", null, null),
                field("dataDirectory", "数据目录", "install", "input", "目录与端口", 30, true, "${installPath}/data", null, null),
                field("apiPort", "API 端口", "config", "number", "目录与端口", 40, true, "9000", Map.of("min", 1, "max", 65535), null),
                field("consolePort", "Console 端口", "config", "number", "目录与端口", 50, true, "9001", Map.of("min", 1, "max", 65535), null),
                field("accessKey", "AccessKey", "config", "input", "账号凭证", 60, true, "minioadmin", null, null),
                field("secretKey", "SecretKey", "config", "password", "账号凭证", 70, true, "minioadmin", null, null),
                field("serviceName", "服务名称", "service", "input", "服务引导", 80, false, "minio-${versionCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 90, false, "true", null, null)
        );
        replaceTemplates(profile,
                template("CONFIG_TEMPLATE", "minio-env", "MinIO 环境配置", "${installPath}/conf/minio.env",
                        "MINIO_ROOT_USER=${accessKey}\nMINIO_ROOT_PASSWORD=${secretKey}\nMINIO_VOLUMES=${dataDirectory}\nMINIO_OPTS=--address :${apiPort} --console-address :${consolePort}\n"),
                template("INSTALL_SCRIPT", "INSTALL_SCRIPT", "MinIO 安装脚本", null,
                        "mkdir -p ${installPath}/conf ${dataDirectory} ${installPath}/logs\n" +
                                "touch ${installPath}/minio\nchmod +x ${installPath}/minio\n")
        );
    }

    private void upsertNacosProfile() {
        SoftPackageProfile profile = upsertProfile(
                "nacos",
                "Nacos",
                "Nacos 注册中心与配置中心安装画像",
                List.of("${installPath}/logs/start.out", "${installPath}/logs/start.err"),
                List.of("${installPath}/conf/application.properties")
        );
        replaceFields(
                profile,
                field("installationName", "实例名称", "install", "input", "基础安装", 10, true, "nacos-${versionCode}", null, null),
                field("installPath", "安装路径", "install", "input", "目录与端口", 20, true, "${baseDirectory}/nacos/${versionCode}", null, null),
                field("dataDirectory", "数据目录", "install", "input", "目录与端口", 30, true, "${installPath}/data", null, null),
                field("httpPort", "HTTP 端口", "config", "number", "目录与端口", 40, true, "8848", Map.of("min", 1, "max", 65535), null),
                field("grpcPort", "gRPC 端口", "config", "number", "目录与端口", 50, true, "9848", Map.of("min", 1, "max", 65535), null),
                field("clusterPort", "集群端口", "config", "number", "目录与端口", 60, true, "9849", Map.of("min", 1, "max", 65535), null),
                field(
                        "mode",
                        "运行模式",
                        "config",
                        "select",
                        "配置初始化",
                        70,
                        true,
                        "standalone",
                        null,
                        List.of(option("standalone", "单机"), option("cluster", "集群"))
                ),
                field("namespace", "命名空间", "config", "input", "配置初始化", 80, false, "public", null, null),
                field("mysqlService", "外置数据库地址", "config", "input", "配置初始化", 90, false, "", null, null),
                field("serviceName", "服务名称", "service", "input", "服务引导", 100, false, "nacos-${versionCode}", null, null),
                field("registerService", "注册服务", "service", "switch", "服务引导", 110, false, "true", null, null)
        );
        replaceTemplates(
                profile,
                template(
                        "CONFIG_TEMPLATE",
                        "nacos-config",
                        "Nacos 主配置",
                        "${installPath}/conf/application.properties",
                        "server.port=${httpPort}\n"
                                + "nacos.core.grpc.port=${grpcPort}\n"
                                + "nacos.core.member.port=${clusterPort}\n"
                                + "nacos.naming.empty-service.auto-clean=true\n"
                                + "spring.application.name=nacos\n"
                                + "nacos.namespace=${namespace}\n"
                                + "spring.sql.init.platform=mysql\n"
                                + "db.url.0=${mysqlService}\n"
                ),
                template(
                        "ENV_TEMPLATE",
                        "nacos-env",
                        "Nacos 环境变量",
                        "${installPath}/conf/nacos.env",
                        "MODE=${mode}\nJAVA_OPT_EXT=-Dnacos.home=${installPath}\n"
                ),
                template(
                        "INSTALL_SCRIPT",
                        "INSTALL_SCRIPT",
                        "Nacos 安装脚本",
                        null,
                        "mkdir -p ${installPath}/bin ${installPath}/conf ${installPath}/logs ${dataDirectory}\n"
                                + "touch ${installPath}/bin/startup.sh ${installPath}/bin/shutdown.sh\n"
                                + "chmod +x ${installPath}/bin/startup.sh ${installPath}/bin/shutdown.sh\n"
                )
        );
    }

    private SoftPackageProfile upsertProfile(String profileCode, String profileName, String description, List<String> logPaths, List<String> configPaths) {
        SoftPackageProfile current = profileMapper.selectOne(Wrappers.<SoftPackageProfile>lambdaQuery()
                .eq(SoftPackageProfile::getProfileCode, profileCode)
                .last("limit 1"));
        if (current == null) {
            current = new SoftPackageProfile();
            current.setProfileCode(profileCode);
        }
        current.setProfileName(profileName);
        current.setPackageCategory(profileName);
        current.setDescription(description);
        current.setBuiltin(Boolean.TRUE);
        current.setEnabled(Boolean.TRUE);
        current.setMetadataJson(SoftJsons.toJson(Map.of("logPaths", logPaths, "configPaths", configPaths)));
        if (current.getSoftPackageProfileId() == null) profileMapper.insert(current); else profileMapper.updateById(current);
        return current;
    }

    private void replaceFields(SoftPackageProfile profile, SoftPackageProfileField... fields) {
        fieldMapper.delete(Wrappers.<SoftPackageProfileField>lambdaQuery().eq(SoftPackageProfileField::getSoftPackageProfileId, profile.getSoftPackageProfileId()));
        for (SoftPackageProfileField field : fields) {
            field.setSoftPackageProfileId(profile.getSoftPackageProfileId());
            fieldMapper.insert(field);
        }
    }

    private void replaceTemplates(SoftPackageProfile profile, SoftPackageProfileTemplate... templates) {
        templateMapper.delete(Wrappers.<SoftPackageProfileTemplate>lambdaQuery().eq(SoftPackageProfileTemplate::getSoftPackageProfileId, profile.getSoftPackageProfileId()));
        for (SoftPackageProfileTemplate template : templates) {
            template.setSoftPackageProfileId(profile.getSoftPackageProfileId());
            templateMapper.insert(template);
        }
    }

    private SoftPackageProfileField field(String key, String label, String scope, String componentType, String groupName, int order, boolean required, String defaultValue, Map<String, Object> validation, List<Map<String, Object>> options) {
        SoftPackageProfileField field = new SoftPackageProfileField();
        field.setFieldKey(key);
        field.setFieldLabel(label);
        field.setFieldScope(scope);
        field.setComponentType(componentType);
        field.setGroupName(groupName);
        field.setSortOrder(order);
        field.setRequiredFlag(required);
        field.setDefaultValue(defaultValue);
        field.setValidationJson(validation == null ? null : SoftJsons.toJson(validation));
        field.setOptionsJson(options == null ? null : SoftJsons.toJson(options));
        field.setMetadataJson("{}");
        return field;
    }

    private SoftPackageProfileTemplate template(String scope, String code, String name, String path, String content) {
        SoftPackageProfileTemplate template = new SoftPackageProfileTemplate();
        template.setTemplateScope(scope);
        template.setTemplateCode(code);
        template.setTemplateName(name);
        template.setTemplatePath(path);
        template.setTemplateEngine("TEXT");
        template.setTemplateContent(content);
        template.setSortOrder(10);
        template.setEnabled(Boolean.TRUE);
        template.setMetadataJson("{}");
        return template;
    }

    private Map<String, Object> option(String value, String label) {
        return Map.of("value", value, "label", label);
    }
}

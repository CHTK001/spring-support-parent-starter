package com.chua.starter.swagger.support;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.swagger")
public class Knife4jProperties {


    /**
     * 是否开启
     */
    private boolean enable = true;

    private List<Knife4j> knife4j;

    /**
     * 账号
     */
    private String username = "root";
    /**
     * 密码
     */
    private String password = "root123";
    /**
     * 是否开启swagger注解记录用户操作日志
     */
    private boolean log;

    @Data
    @Accessors(chain = true)
    public static class Knife4j {
        /**
         * 描述
         */
        private String description = "无";
        /**
         * 版本
         */
        private String version = "1.0";
        /**
         * 分组名
         */
        private String groupName;

        /**
         * 路径 openapi
         */
        private String[] pathsToMatch;
        /**
         * basePackage
         */
        private String[] basePackage;

        /**
         * 条款初始化服务
         */
        private String termsOfService = "1.服务提供方：本条款中的“服务提供方”指指定的公司或组织，提供API服务。<br>\n" +
                "  2.用户：本条款中的“用户”指使用服务提供方提供的API服务的个人、公司或组织。<br>\n" +
                "  3.授权：用户在遵守本条款的前提下，服务提供方授予用户使用其API服务的权利。<br>\n" +
                "  4.使用限制：用户在使用API服务时，必须遵守以下限制：<br>\n" +
                "  &nbsp;&nbsp;&nbsp;&nbsp;a.用户不得使用API服务进行非法活动或违反法律法规的行为；<br>\n" +
                "  &nbsp;&nbsp;&nbsp;&nbsp;b.用户不得以任何方式干扰、破坏或损害API服务的正常运行；<br>\n" +
                "  &nbsp;&nbsp;&nbsp;&nbsp;c.用户不得滥用API服务，包括但不限于超出使用限制、恶意攻击等行为；<br>\n" +
                "  &nbsp;&nbsp;&nbsp;&nbsp;d.用户不得将API服务用于未经授权的商业用途。<br>\n" +
                "  5.账户安全：用户在使用API服务时，必须保护其账户的安全性，包括但不限于保护密码和API密钥的安全。<br>\n" +
                "  6.责任限制：服务提供方对于用户使用API服务所导致的任何直接或间接损失不承担责任，包括但不限于利润损失、数据损失等。<br>\n" +
                "  7.服务变更和终止：服务提供方有权根据需要随时变更或终止API服务，用户在此不得要求服务提供方承担任何责任。<br\\n" +
                "  8.知识产权：服务提供方保留所有API服务相关的知识产权。<br>9.隐私保护：服务提供方将根据相关法律法规保护用户的个人信息和数据隐私。<br>\n" +
                "  10.争议解决：本条款的解释和适用以及由此产生的争议应适用服务提供方所在地的法律。<br>\n" +
                "  11.其他条款：本条款中的任何条款无效或不可执行时，不影响其他条款的效力。<br>\n" +
                "  12.条款变更：服务提供方有权随时修改本条款，并通过适当的方式向用户通知。<br>\n" +
                "  \uD83D\uDCE2请用户在使用API服务之前仔细阅读并理解本条款，如用户不同意本条款的任何内容，请勿使用API服务。<br>\n" +
                "  ⚠\uFE0F用户一旦使用API服务，即视为用户已接受本条款的全部内容。";
    }
}

package com.chua.starter.account.configuration;

import com.chua.starter.account.template.MessageTemplateController;
import com.chua.starter.account.template.MessageTemplateService;
import com.chua.starter.account.mail.MailboxController;
import com.chua.starter.account.mail.MailboxService;
import com.chua.starter.account.mail.SmsTemplateSyncController;
import com.chua.starter.account.mail.SmsTemplateSyncService;
import com.chua.starter.account.mail.temp.TempMailController;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.controller.AccountAppKeyController;
import com.chua.starter.account.manage.controller.AccountController;
import com.chua.starter.account.manage.controller.AccountCookieController;
import com.chua.starter.account.manage.controller.AccountSecretController;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.impl.SysAccountAppKeyServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountCookieServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountSecretServiceImpl;
import com.chua.starter.account.manage.service.impl.ManageAccountServiceImpl;
import com.chua.starter.account.manage.support.AccountApiExceptionAdvice;
import com.chua.starter.account.web.ChatChannelController;
import com.chua.starter.account.web.ImageChannelController;
import com.chua.starter.account.web.Model3DChannelController;
import com.chua.starter.account.web.VideoChannelController;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 账号与渠道模块自动配置。
 *
 * <p>宿主引入 {@code spring-support-account-starter} 即获得：</p>
 * <ul>
 *   <li>渠道管理：免登录 ChatClient（{@code /api/channels/{channel}/chat}）、文生图、文生视频、图生 3D</li>
 *   <li>账号管理：统一账号的增删改查、密钥加密、归属隔离与引用校验</li>
 * </ul>
 *
 * <p>本模块不持有任何 {@code sys_ai_*} 表，也不依赖 AI 模块；AI 若要用渠道能力，
 * 只按 SPI 解析 {@code ChatChannelProvider}，与账号数据完全解耦。</p>
 *
 * <p>扫描口径按类白名单固定，避免宿主 classpath 上还有兄弟模块时装配结果漂移。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@AutoConfiguration
@ComponentScan(
        basePackages = {
                // 免登录对话 / 文生图渠道：/api/channels/{channel}/chat、/images
                "com.chua.starter.account.web",
                // 短信 / 邮件消息模板：/api/channels/templates
                "com.chua.starter.account.template",
                // 统一账号管理：/api/accounts
                "com.chua.starter.account.manage",
                // 收 / 发信箱：/api/accounts/{accountId}/mailbox
                "com.chua.starter.account.mail"
        },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ChatChannelController.class,
                        ImageChannelController.class,
                        VideoChannelController.class,
                        Model3DChannelController.class,
                        MessageTemplateController.class,
                        MessageTemplateService.class,
                        AccountController.class,
                        AccountAppKeyController.class,
                        AccountCookieController.class,
                        AccountSecretController.class,
                        ManageAccountServiceImpl.class,
                        SysAccountAppKeyServiceImpl.class,
                        SysAccountCookieServiceImpl.class,
                        SysAccountSecretServiceImpl.class,
                        AccountSecretCodec.class,
                        AccountApiExceptionAdvice.class,
                        MailboxController.class,
                        MailboxService.class,
                        SmsTemplateSyncController.class,
                        SmsTemplateSyncService.class,
                        TempMailController.class,
                        TempMailService.class
                })
)
@MapperScan({
        // 短信 / 邮件消息模板持久化
        "com.chua.starter.account.template.mapper",
        // 统一账号持久化
        "com.chua.starter.account.manage.mapper"
})
public class AccountAutoConfiguration {

    /**
     * 构造方法。
     *
     * <p>启动日志留下可检索锚点，便于排查「账号/渠道能力是否被装配」。</p>
     */
    public AccountAutoConfiguration() {
        log.info("[account] 账号与渠道模块已装配：账号管理 /api/accounts、免登录对话 /api/channels/{channel}/chat、"
                + "文生图 /api/channels/{channel}/images、文生视频 /api/channels/{channel}/videos、"
                + "图生 3D /api/channels/{channel}/3d-models、消息模板 /api/channels/templates");
    }
}

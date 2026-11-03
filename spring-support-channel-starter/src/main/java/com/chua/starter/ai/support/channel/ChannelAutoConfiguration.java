package com.chua.starter.ai.support.channel;

import com.chua.starter.ai.support.controller.settings.AiChannelHealthController;
import com.chua.starter.ai.support.manage.api.ApiChannelAuthController;
import com.chua.starter.ai.support.manage.schedule.ChannelCheckinTask;
import com.chua.starter.ai.support.manage.schedule.ChannelHealthProbeTask;
import com.chua.starter.ai.support.service.impl.ChannelHealthServiceImpl;
import com.chua.starter.channel.tempaccount.TempAccountController;
import com.chua.starter.channel.tempaccount.TempAccountService;
import com.chua.starter.channel.tempaccount.TempAccountStreamRegistry;
import com.chua.starter.channel.template.MessageTemplateController;
import com.chua.starter.channel.template.MessageTemplateService;
import com.chua.starter.channel.web.ChatChannelController;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 渠道管理模块自动配置。
 *
 * <p>宿主引入 {@code spring-support-channel-starter} 依赖即获得渠道账号鉴权
 * （{@code /api/channels}）、渠道健康探针（{@code /v2/channels}）与渠道定时任务的装配。</p>
 *
 * <h2>为什么按「类」而不是按「包」挑选</h2>
 * <p>本模块的类大多沿用 {@code com.chua.starter.ai.support.*} 包名（保证 AI 侧既有 import 与前端契约
 * 完全不变），这些包与 {@code spring-support-ai-starter} 交叠。若用默认过滤器整包扫描，装配结果
 * 会随「classpath 上还有哪些兄弟模块」漂移；这里用 {@code useDefaultFilters = false} +
 * {@code ASSIGNABLE_TYPE} 白名单，只装配本模块真正拥有的八个类，与兄弟模块的类数量无关。
 * 后迁入的临时账号编排（{@code com.chua.starter.channel.tempaccount}）独占包名不与其他模块交叠，
 * 同样按类白名单显式装配。</p>
 *
 * <h2>与 ai-starter 共存时不会重复注册</h2>
 * <p>{@code AiConfiguration} 会整包扫描 {@code com.chua.starter.ai.support}，因此同进程引入两个模块时
 * 这些类被扫描两次。<b>Bean 名称相同</b>（注解扫描一律用类简名首字母小写），MyBatis 的
 * {@code ClassPathMapperScanner} 对同名 {@code MapperFactoryBean} 也会显式跳过，容器里始终只有一份
 * 实例、路由表也只注册一次，不会出现 {@code Ambiguous mapping}。</p>
 *
 * <h2>运行期依赖</h2>
 * <p>本模块自带渠道相关实体、Mapper、服务与 SPI 实现；只把
 * {@code GlobalSettingReader} / {@code SysAiGlobalSettingService} 的<b>接口</b>带在模块内——
 * 两者的实现与 {@code sys_ai_global_setting} 持久化属于「全局设置」基础设施，仍由
 * {@code spring-support-ai-starter} 提供。依赖方向固定为
 * {@code spring-support-ai-starter → spring-support-channel-starter}，无反向依赖。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@AutoConfiguration
@ComponentScan(
        basePackages = {
                // 渠道账号鉴权控制器：/api/channels
                "com.chua.starter.ai.support.manage.api",
                // 渠道健康探针控制器：/v2/channels
                "com.chua.starter.ai.support.controller.settings",
                // 定时任务：自动签到、定期探针
                "com.chua.starter.ai.support.manage.schedule",
                // 健康探针状态机实现
                "com.chua.starter.ai.support.service.impl",
                // 临时账号编排（从 monitor 迁入）：/api/channels/temp-accounts
                "com.chua.starter.channel.tempaccount",
                // 免登录对话渠道（ChatClient SPI）：/api/channels/{channel}/chat
                "com.chua.starter.channel.web",
                // 短信 / 邮件消息模板：/api/channels/templates
                "com.chua.starter.channel.template"
        },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ApiChannelAuthController.class,
                        AiChannelHealthController.class,
                        ChannelCheckinTask.class,
                        ChannelHealthProbeTask.class,
                        ChannelHealthServiceImpl.class,
                        TempAccountController.class,
                        TempAccountService.class,
                        TempAccountStreamRegistry.class,
                        ChatChannelController.class,
                        MessageTemplateController.class,
                        MessageTemplateService.class
                })
)
@MapperScan({
        // 渠道健康记录 + 提供商 / 连接 / 模型持久化（本模块自包含）
        "com.chua.starter.ai.support.manage.mapper",
        // 临时账号身份 / 消息持久化
        "com.chua.starter.channel.tempaccount.mapper",
        // 短信 / 邮件消息模板持久化
        "com.chua.starter.channel.template.mapper"
})
public class ChannelAutoConfiguration {

    /**
     * 构造方法。
     *
     * <p>在启动日志留下可检索的锚点，便于排查「渠道能力是否被装配」。</p>
     */
    public ChannelAutoConfiguration() {
        log.info("[channel] 渠道管理模块已装配：账号鉴权 /api/channels、免登录对话 /api/channels/{channel}/chat、"
                + "消息模板 /api/channels/templates、健康探针 /v2/channels、临时账号 /api/channels/temp-accounts、定时签到与探针");
    }
}

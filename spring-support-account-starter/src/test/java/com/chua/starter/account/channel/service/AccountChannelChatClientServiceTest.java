package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.chua.common.support.ai.chat.aggregate.MultiKeyChatClient;
import com.chua.starter.account.channel.mapper.ChannelHealthLogMapper;
import com.chua.starter.account.channel.mapper.ChannelHealthMapper;
import com.chua.starter.account.channel.mapper.SysAccountChannelConnectionMapper;
import com.chua.starter.account.channel.mapper.SysAccountChannelMapper;
import com.chua.starter.account.channel.mapper.SysAccountChannelModelMapper;
import com.chua.starter.account.channel.store.ChannelHealth;
import com.chua.starter.account.channel.store.ChannelHealthLog;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.account.common.ApiException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 H2、MyBatis 与客户端 SPI 验证渠道供给契约，不调用外部模型。
 * @author CH
 */
class AccountChannelChatClientServiceTest {
    /** 实际数据库会话。 */
    private SqlSession session;
    /** 实际渠道 Mapper。 */
    private SysAccountChannelMapper channels;
    /** 实际模型 Mapper。 */
    private SysAccountChannelModelMapper models;
    /** 被测供给服务。 */
    private AccountChannelChatClientService service;

    /** 按实体列元数据创建独立真实测试库。 */
    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (Class<?> entity : List.of(SysAccountChannel.class, SysAccountChannelModel.class,
                SysAccountChannelConnection.class, ChannelHealth.class, ChannelHealthLog.class)) {
            List<String> columns = new ArrayList<>();
            for (var field : entity.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                TableField column = field.getAnnotation(TableField.class);
                TableId id = field.getAnnotation(TableId.class);
                if (column == null && id == null) continue;
                String name = column == null ? id.value() : column.value();
                String type = field.getType() == Boolean.class ? "BOOLEAN" : Number.class.isAssignableFrom(field.getType()) ? "BIGINT" : "VARCHAR(10000)";
                columns.add(name + " " + type);
            }
            jdbc.execute("CREATE TABLE " + entity.getAnnotation(TableName.class).value() + " (" + String.join(",", columns) + ")");
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        for (Class<?> mapper : List.of(SysAccountChannelMapper.class, SysAccountChannelModelMapper.class,
                SysAccountChannelConnectionMapper.class, ChannelHealthMapper.class, ChannelHealthLogMapper.class)) configuration.addMapper(mapper);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        channels = session.getMapper(SysAccountChannelMapper.class);
        models = session.getMapper(SysAccountChannelModelMapper.class);
        var events = new StaticApplicationContext();
        var channelService = new AccountChannelService(channels, models, session.getMapper(SysAccountChannelConnectionMapper.class), events);
        service = new AccountChannelChatClientService(channelService, new AccountChannelModelService(models, events),
                new AccountChannelConnectionService(session.getMapper(SysAccountChannelConnectionMapper.class), channelService, events),
                new AccountChannelHealthService(session.getMapper(ChannelHealthMapper.class), session.getMapper(ChannelHealthLogMapper.class)));
    }

    /** 释放真实 JDBC 会话。 */
    @AfterEach
    void close() { if (session != null) session.close(); }

    /** 同名模型按渠道取客户端，且每次独立构建、认证头不丢。 */
    @Test
    void modelIdentityAndClientStateAreIndependent() {
        insert("one", "https://one.example/v1");
        insert("two", "https://two.example/v1");
        try (var first = service.create("one", "same-model", "_sys", null);
             var second = service.create("two", "same-model", "_sys", null)) {
            assertNotSame(first, second);
            assertInstanceOf(MultiKeyChatClient.class, first);
            assertEquals("https://one.example/v1", first.getSetting().getBaseUrl());
            assertEquals("https://two.example/v1", second.getSetting().getBaseUrl());
            assertEquals("api-key", first.getSetting().getAuthHeader());
            assertEquals("", first.getSetting().getAuthPrefix());
        }
    }

    /** 停用渠道或模型不再供给，不能落到另一渠道同名模型。 */
    @Test
    void disabledChannelOrModelFailsClosed() {
        insert("one", "https://one.example/v1");
        var channel = channels.selectById("one");
        channel.setEnabled(false); channels.updateById(channel);
        assertThrows(ApiException.class, () -> service.create("one", "same-model", "_sys", null));
        channel.setEnabled(true); channels.updateById(channel);
        var model = models.selectById("same-model"); model.setEnabled(false); models.updateById(model);
        assertThrows(ApiException.class, () -> service.create("one", "same-model", "_sys", null));
    }

    /** @param id 渠道标识 @param url 上游地址 */
    private void insert(String id, String url) {
        var channel = new SysAccountChannel(); channel.setProviderId(id); channel.setName(id);
        channel.setEnabled(true); channel.setPlatform("free-channel"); channel.setBaseUrl(url);
        channel.setAuthScheme("api_key"); channel.setCredentials(Map.of("api_key", "acceptance-key"));
        channel.setExtra(Map.of("auth_header", "api-key", "auth_prefix", "")); channels.insert(channel);
        var model = new SysAccountChannelModel(); model.setProviderId(id); model.setModel("same-model"); model.setEnabled(true); models.insert(model);
    }
}

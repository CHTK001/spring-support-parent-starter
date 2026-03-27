package com.chua.starter.oauth.client.support.protocol;

import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "oauth.real.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtocolRealIntegrationTest {

    private static final String LOGIN_PASSWORD_MD5 = "0192023a7bbd73250516f069df18b500";
    private StaticApplicationContext applicationContext;

    @BeforeAll
    void setUp() {
        applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        SpringBeanUtils.setApplicationContext(applicationContext);
        AbstractProtocol.invalidateAllCache();
    }

    @AfterAll
    void tearDown() {
        AbstractProtocol.invalidateAllCache();
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @DisplayName("真实登录联调")
    @ParameterizedTest(name = "{0} encryption={1}")
    @MethodSource("protocolMatrix")
    void shouldLoginAcrossProtocols(String protocol, boolean enableEncryption) {
        Protocol protocolClient = newProtocol(protocol, enableEncryption);

        LoginAuthResult result = protocolClient.getAccessToken("sa", LOGIN_PASSWORD_MD5, AuthType.NONE, Map.of());

        assertEquals(200, result.getCode(), protocol + " login failed: " + result.getMessage());
        assertNotNull(result.getToken(), protocol + " login token is null");
        assertNotNull(result.getRefreshToken(), protocol + " refresh token is null");
        assertNotNull(result.getUserResume(), protocol + " login user is null");
        assertEquals("sa", result.getUserResume().getUsername(), protocol + " login user mismatch");
    }

    @DisplayName("真实 AppKey 鉴权联调")
    @ParameterizedTest(name = "{0} encryption={1}")
    @MethodSource("protocolMatrix")
    void shouldAuthenticateAppKeyAcrossProtocols(String protocol, boolean enableEncryption) {
        Protocol protocolClient = newProtocol(protocol, enableEncryption);
        AppKeySecret appKeySecret = new AppKeySecret()
                .setUserCode("test-app-key-001")
                .setBody("dummy-code");

        AuthenticationInformation result = protocolClient.authentication(appKeySecret);

        assertEquals(200, result.getInformation().getCode(), protocol + " appKey failed: " + messageOf(result));
        assertNotNull(result.getReturnResult(), protocol + " appKey user is null");
        assertEquals("1", result.getReturnResult().getUserId(), protocol + " appKey userId mismatch");
    }

    private static Stream<Arguments> protocolMatrix() {
        return Stream.of(
                Arguments.of("http", true),
                Arguments.of("http", false),
                Arguments.of("armeria", true),
                Arguments.of("armeria", false),
                Arguments.of("rsocket", true),
                Arguments.of("rsocket", false)
        );
    }

    private Protocol newProtocol(String protocol, boolean enableEncryption) {
        AuthClientProperties properties = new AuthClientProperties();
        properties.setProtocol(protocol);
        properties.setEnableEncryption(enableEncryption);
        properties.setAddress(resolveAddress(protocol));
        properties.setLoginPage("/oauth/login");
        properties.setLogoutPage("/oauth/logout");
        properties.setOauthUrl("/oauth/verify");
        properties.setTemporaryTokenPage("/oauth/temporary-token");
        properties.setUpgradePage("/oauth/upgrade");

        return switch (protocol) {
            case "http" -> new HttpProtocol(properties);
            case "armeria" -> new ArmeriaProtocol(properties);
            case "rsocket" -> new RSocketProtocol(properties);
            default -> throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        };
    }

    private String resolveAddress(String protocol) {
        if ("rsocket".equals(protocol)) {
            return "rsocket://127.0.0.1:7000";
        }
        return "http://127.0.0.1:19180";
    }

    private String messageOf(AuthenticationInformation information) {
        if (information == null) {
            return "null response";
        }
        if (information.getErrorMessage() != null && !information.getErrorMessage().isEmpty()) {
            return information.getErrorMessage();
        }
        return information.getInformation() != null ? information.getInformation().getMessage() : "unknown";
    }
}

package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.text.json.JsonObject;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpProtocolLoginResultTest {

    @Test
    void shouldPreferServerErrorMessageAndKeepBlankTokensNull() {
        AuthenticationInformation authenticationInformation =
                new AuthenticationInformation(Information.AUTHENTICATION_FAILURE, new UserResume("login failed"));
        authenticationInformation.setErrorMessage("账号或密码错误");
        authenticationInformation.setToken("");
        authenticationInformation.setRefreshToken(" ");

        TestHttpProtocol protocol = new TestHttpProtocol(authenticationInformation);

        LoginAuthResult result = protocol.getAccessToken("sa", "md5-password", AuthType.NONE, Map.of());

        assertThat(result.getCode()).isEqualTo(Information.AUTHENTICATION_FAILURE.getCode());
        assertThat(result.getMessage()).isEqualTo("账号或密码错误");
        assertThat(result.getToken()).isNull();
        assertThat(result.getRefreshToken()).isNull();
    }

    @Test
    void shouldCopyTokensAndUserResumeWhenAuthenticationSucceeds() {
        UserResume userResume = new UserResume();
        userResume.setUsername("sa");

        AuthenticationInformation authenticationInformation =
                new AuthenticationInformation(Information.OK, userResume);
        authenticationInformation.setToken("access-token");
        authenticationInformation.setRefreshToken("refresh-token");

        TestHttpProtocol protocol = new TestHttpProtocol(authenticationInformation);

        LoginAuthResult result = protocol.getAccessToken("sa", "md5-password", AuthType.WEB, Map.of("tenant", "default"));

        assertThat(result.getCode()).isEqualTo(Information.OK.getCode());
        assertThat(result.getMessage()).isEqualTo(Information.OK.getMessage());
        assertThat(result.getToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getUserResume()).isSameAs(userResume);
    }

    private static final class TestHttpProtocol extends HttpProtocol {

        private final AuthenticationInformation authenticationInformation;

        private TestHttpProtocol(AuthenticationInformation authenticationInformation) {
            super(new AuthClientProperties());
            this.authenticationInformation = authenticationInformation;
        }

        @Override
        protected AuthenticationInformation createAuthenticationInformation(
                JsonObject jsonObject, UpgradeType upgradeType, String path) {
            return authenticationInformation;
        }
    }
}

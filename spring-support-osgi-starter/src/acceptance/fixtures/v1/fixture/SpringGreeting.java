package fixture;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;
import com.example.spi.GreetingApi;

/** First independently packaged plugin version. */
@Spi(value = "spring-live", order = 77)
@SpiDefault
public final class SpringGreeting implements GreetingApi {
    @Override
    public String greet(String name) {
        return "v1:" + name;
    }
}

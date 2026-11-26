package fixture;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;
import com.example.spi.GreetingApi;

/** Replacement version of the same plugin. */
@Spi(value = "spring-live", order = 77)
@SpiDefault
public final class SpringGreeting implements GreetingApi {
    @Override
    public String greet(String name) {
        return "v2:" + name;
    }
}

package com.dilaw.mohe.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-04: Tomcat believes {@code X-Forwarded-For} from no peer unless the
 * deployment names its proxy.
 *
 * <p>With {@code server.forward-headers-strategy=native} Spring Boot installs
 * Tomcat's {@link RemoteIpValve}, whose default {@code internalProxies} is every
 * private-range and loopback address. Any client inside the network could then
 * choose its own address: a fresh login-throttle bucket per request, a pass
 * through the admin IP allowlist, a forged audit IP.
 */
class TrustedProxiesDefaultTest {

    private static final String KEY = "server.tomcat.remoteip.internal-proxies";

    private static StandardEnvironment environment(Map<String, Object> env) throws Exception {
        Properties props = new Properties();
        try (InputStream in = TrustedProxiesDefaultTest.class.getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("env", env));
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", props));
        return environment;
    }

    @Test
    @DisplayName("Tomcat's own default would trust a 10.x peer: that is what the property overrides")
    void tomcatDefaultTrustsPrivateRanges() {
        String tomcatDefault = new RemoteIpValve().getInternalProxies();
        assertThat(Pattern.compile(tomcatDefault).matcher("10.1.2.3").matches()).isTrue();
    }

    @Test
    @DisplayName("by default no peer is trusted: the valve gets an empty pattern, which Tomcat reads as none")
    void defaultTrustsNoPeer() throws Exception {
        String value = environment(Map.of()).getProperty(KEY);
        assertThat(value).as("the property is set, so Tomcat's default does not apply").isNotNull().isEmpty();

        RemoteIpValve valve = new RemoteIpValve();
        valve.setInternalProxies(value);
        assertThat(valve.getInternalProxies()).as("no internal proxy at all").isNull();
    }

    @Test
    @DisplayName("a real proxy deployment names its proxy through MOHE_TRUSTED_PROXIES")
    void overridableForARealProxy() throws Exception {
        String value = environment(Map.of("MOHE_TRUSTED_PROXIES", "10\\.0\\.4\\.17")).getProperty(KEY);

        Pattern trusted = Pattern.compile(value);
        assertThat(trusted.matcher("10.0.4.17").matches()).isTrue();
        assertThat(trusted.matcher("10.0.4.18").matches()).isFalse();
    }
}

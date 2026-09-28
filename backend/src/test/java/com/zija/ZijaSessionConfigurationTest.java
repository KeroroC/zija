package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZijaSessionConfigurationTest {

    private final ZijaSessionConfiguration configuration = new ZijaSessionConfiguration();

    @Test
    void writesHttpOnlyAndSameSiteFromConfiguration() {
        var header = writeCookie(configuration.cookieSerializer("ZIJA_SESSION", true, "lax"), false);

        assertThat(header).startsWith("ZIJA_SESSION=");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).doesNotContain("Secure");
    }

    @Test
    void canDisableHttpOnlyAndUseStrict() {
        var header = writeCookie(configuration.cookieSerializer("ZIJA_SESSION", false, "strict"), false);

        assertThat(header).doesNotContain("HttpOnly");
        assertThat(header).contains("SameSite=Strict");
    }

    @Test
    void secureStillFollowsTransport() {
        var serializer = configuration.cookieSerializer("ZIJA_SESSION", true, "lax");

        assertThat(writeCookie(serializer, true)).contains("Secure");
        assertThat(writeCookie(serializer, false)).doesNotContain("Secure");
    }

    @Test
    void rejectsUnknownSameSite() {
        assertThatThrownBy(() -> configuration.cookieSerializer("ZIJA_SESSION", true, "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("server.servlet.session.cookie.same-site must be Lax, Strict, or None");
    }

    private static String writeCookie(CookieSerializer serializer, boolean secure) {
        var request = new MockHttpServletRequest();
        request.setSecure(secure);
        var response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "token"));
        return response.getHeader("Set-Cookie");
    }
}

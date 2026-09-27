package com.zija;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ZijaFallbackExceptionHandlerTest.ThrowingController.class)
@Import({ZijaFallbackExceptionHandler.class, ZijaFallbackExceptionHandlerTest.ThrowingController.class})
class ZijaFallbackExceptionHandlerTest extends AbstractWebMvcSliceTest {

    @Autowired
    private MockMvc mvc;

    private final RequestPostProcessor member = SecurityMockMvcRequestPostProcessors.user(
            new ZijaPrincipal(UUID.randomUUID(), "member", "成员", "hash", true));

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void captureLogs() {
        handlerLogger = (Logger) LoggerFactory.getLogger(ZijaFallbackExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        handlerLogger.detachAppender(appender);
    }

    @Test
    void unexpectedExceptionBecomesInternalErrorProblemAndIsLogged() throws Exception {
        mvc.perform(get("/test/boom").with(member).header("X-Request-Id", "boom-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value("boom-1"))
                .andExpect(jsonPath("$.detail", not(containsString("secret-internal-state"))));

        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getThrowableProxy()).isNotNull();
                    assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "boom-1");
                });
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(get("/test/not-found").with(member))
                .andExpect(status().isNotFound());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void accessDeniedStillHandledBySecurityLayer() throws Exception {
        mvc.perform(get("/test/denied").with(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        assertThat(appender.list).isEmpty();
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-state");
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/test/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }
    }
}

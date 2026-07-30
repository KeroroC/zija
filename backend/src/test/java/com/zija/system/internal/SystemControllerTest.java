package com.zija.system.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.system.SystemApi;
import com.zija.system.internal.exception.SystemStateUnavailableException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemController.class)
@Import(SystemExceptionHandler.class)
class SystemControllerTest extends AbstractWebMvcSliceTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsPublicSystemInformation() throws Exception {
        var installationId =
                UUID.fromString("34bf30dd-d082-4e26-9dfe-8f30421f4772");
        var databaseTime =
                OffsetDateTime.parse("2026-07-19T12:00:00Z");
        given(systemApi.current()).willReturn(new SystemApi.SystemSnapshot(
                "zija",
                "dev",
                "UP",
                installationId,
                databaseTime
        ));

        mvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.application").value("zija"))
                .andExpect(jsonPath("$.version").value("dev"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.installationId")
                        .value(installationId.toString()))
                .andExpect(jsonPath("$.databaseTime")
                        .value("2026-07-19T12:00:00Z"));
    }

    @Test
    void returnsProblemDetailsWithStableCodeAndRequestId() throws Exception {
        given(systemApi.current())
                .willThrow(new SystemStateUnavailableException(
                        "installation missing"
                ));

        mvc.perform(get("/api/v1/system/info")
                        .header("X-Request-Id", "request-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("System state unavailable"))
                .andExpect(jsonPath("$.errorCode")
                        .value("system_state_unavailable"))
                .andExpect(jsonPath("$.requestId").value("request-123"));
    }

    @Test
    void replacesUnsafeRequestIdBeforeWritingResponseHeaders() throws Exception {
        given(systemApi.current()).willReturn(new SystemApi.SystemSnapshot(
                "zija",
                "dev",
                "UP",
                UUID.fromString("34bf30dd-d082-4e26-9dfe-8f30421f4772"),
                OffsetDateTime.parse("2026-07-19T12:00:00Z")
        ));

        mvc.perform(get("/api/v1/system/info")
                        .header("X-Request-Id", "unsafe request id"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Request-Id",
                        matchesPattern(
                                "[0-9a-f]{8}-[0-9a-f]{4}-"
                                        + "[0-9a-f]{4}-[0-9a-f]{4}-"
                                        + "[0-9a-f]{12}"
                        )
                ));
    }

    @Test
    void returnsStableProblemDetailsForDatabaseFailures() throws Exception {
        given(systemApi.current()).willThrow(
                new CannotCreateTransactionException("database unavailable")
        );

        mvc.perform(get("/api/v1/system/info")
                        .header("X-Request-Id", "database-request-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("System state unavailable"))
                .andExpect(jsonPath("$.errorCode")
                        .value("system_state_unavailable"))
                .andExpect(jsonPath("$.requestId")
                        .value("database-request-123"));
    }

    @Test
    void permitsServletErrorDispatches() throws Exception {
        mvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(
                                    DispatcherType.ERROR
                            );
                            return request;
                        })
                        .requestAttr(
                                RequestDispatcher.ERROR_STATUS_CODE,
                                500
                        )
                        .requestAttr(
                                RequestDispatcher.ERROR_REQUEST_URI,
                                "/api/v1/system/info"
                        ))
                .andExpect(status().isInternalServerError());
    }
}

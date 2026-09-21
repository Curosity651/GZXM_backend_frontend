package com.gzxm.server.modules.systemlog.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestTraceFilterTest {
    private final SystemErrorLogService logs = mock(SystemErrorLogService.class);
    private final RequestTraceFilter filter = new RequestTraceFilter(logs);

    @Test
    void recordsFailedRequestWithCallerTraceId() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/achievements");
        request.addHeader("X-Request-Id", "client-trace-17");
        request.setAttribute(RequestErrorContext.CODE, "VALIDATION_ERROR");
        request.setAttribute(RequestErrorContext.MESSAGE, "字段不完整");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(422));

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("client-trace-17");
        verify(logs).record(eq("client-trace-17"), eq("POST"), eq("/api/v1/achievements"), eq(422),
                eq("VALIDATION_ERROR"), eq("字段不完整"), isNull(), anyLong(), any(), any());
    }

    @Test
    void successfulRequestIsNotPersisted() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        verifyNoInteractions(logs);
    }
}

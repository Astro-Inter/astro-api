package com.astro.api.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeExceptionLoggingTest {
    @Test
    void preservaCodigoEFramesSemMensagemDoDriver() {
        SQLException source = new SQLException("SQL private@example.com password=secret", "53300");
        Throwable safe = SafeExceptionLogging.safe(source,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        assertThat(safe.getMessage()).contains("SQLException", "53300").doesNotContain("private", "secret", "password");
        assertThat(safe.getStackTrace()).isEqualTo(source.getStackTrace());
    }

    @Test
    void compartilhaIdDentroDaRequisicaoERestauraContextoAposErro() {
        MDC.put("request_id", "previous");
        try {
            var request = new MockHttpServletRequest("GET", "/private/42");
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> new HttpRequestLoggingFilter().doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("request_id")).isEqualTo(response.getHeader("X-Request-ID"));
                throw new IllegalStateException("private");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get("request_id")).isEqualTo("previous");
        } finally { MDC.remove("request_id"); }
    }
}

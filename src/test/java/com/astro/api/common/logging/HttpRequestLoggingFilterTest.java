package com.astro.api.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpRequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new HttpRequestLoggingFilter()).build();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void registraRotaStatusTempoEIdSemDadosSensiveis() throws Exception {
        var response = mvc.perform(get("/items/42")
                        .queryParam("token", "segredo-query")
                        .header("Authorization", "Bearer segredo-token")
                        .header("X-Request-ID", "id-externo")
                        .content("segredo-corpo"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("method=GET", "route=/items/{id}", "status=200")
                .doesNotContain("segredo", "id-externo", "/items/42");
        var fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "http_request")
                .containsEntry("http_route", "/items/{id}").containsEntry("http_status", 200);
        assertThat((Long) fields.get("duration_ms")).isGreaterThanOrEqualTo(0);
        assertThat(UUID.fromString(response.getHeader("X-Request-ID")).toString())
                .isEqualTo(fields.get("request_id"));
    }

    @Test
    void registraErrosRespondidosPeloEndpoint() throws Exception {
        mvc.perform(get("/failure")).andExpect(status().isInternalServerError());
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("status=500");
    }

    @Test
    void registraRejeicaoAntesDeChegarAoController() throws Exception {
        Filter reject = (request, response, chain) -> ((jakarta.servlet.http.HttpServletResponse) response)
                .sendError(403);
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new HttpRequestLoggingFilter(), reject).build();
        mvc.perform(get("/items/42")).andExpect(status().isForbidden());
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("route=/items/42", "status=403");
    }

    @Test
    void registra500EPropagaExcecaoSemExporMensagem() {
        var filter = new HttpRequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/failure");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("segredo-interno");
        })).isInstanceOf(ServletException.class);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("status=500")
                .doesNotContain("segredo-interno");
    }

    @RestController
    static class TestController {
        @GetMapping("/items/{id}")
        String item(@PathVariable String id) {
            return id;
        }

        @GetMapping("/failure")
        ResponseEntity<Void> failure() {
            return ResponseEntity.internalServerError().build();
        }
    }
}

package com.astro.api.conformity;

import com.astro.api.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.junit.jupiter.api.Assertions.*;

class ConformityMissingRouteTest {
    @Test void removedAndUnknownRoutesReturnNotFoundWithApiResult() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/conformities");
        for (Exception failure : new Exception[]{new NoResourceFoundException(HttpMethod.GET, "conformities", "/conformities"),
                new NoHandlerFoundException("GET", "/conformities", new HttpHeaders())}) {
            var response = handler.handleRouteNotFound(failure, request);
            assertEquals(404, response.getStatusCode().value());
            assertNotNull(response.getBody()); assertFalse(response.getBody().success());
            assertEquals("/conformities", response.getBody().path());
            assertEquals("Recurso não encontrado", response.getBody().message());
        }
    }
}

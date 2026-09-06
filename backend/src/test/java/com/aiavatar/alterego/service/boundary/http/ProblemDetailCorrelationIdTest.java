package com.aiavatar.alterego.service.boundary.http;

import com.aiavatar.alterego.boundary.http.AlterEgoController;
import com.aiavatar.alterego.boundary.http.CorrelationIdFilter;
import com.aiavatar.alterego.boundary.http.ProblemDetailAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T060 — every Problem-Detail response carries the request's correlationId
 * (FR-2412). Drives a malformed Generate request through the controller +
 * advice, with the {@link CorrelationIdFilter} populating the MDC.
 */
@WebMvcTest(AlterEgoController.class)
@Import(ProblemDetailAdvice.class)
class ProblemDetailCorrelationIdTest {

    @Autowired private MockMvc mvc;
    @MockBean private com.aiavatar.alterego.application.AlterEgoUseCase useCase;

    @Test
    void problemDetailBodyCarriesCorrelationIdFromMdc() throws Exception {
        // Filter accepts inbound X-Correlation-Id when alphanumeric ≤ 64.
        mvc.perform(post("/api/v1/alter-egos")
                        .header(CorrelationIdFilter.HEADER, "TEST12345678")
                        .contentType("application/json"))  // wrong content type → 415
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.correlationId").value("TEST12345678"));
    }
}

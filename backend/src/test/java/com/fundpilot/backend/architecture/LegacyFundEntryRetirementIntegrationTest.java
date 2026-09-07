package com.fundpilot.backend.architecture;

import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class LegacyFundEntryRetirementIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void 旧基金增删改查入口均返回路由未找到() throws Exception {
        mockMvc.perform(get("/api/funds")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/funds")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/funds/17")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/funds/17")
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}

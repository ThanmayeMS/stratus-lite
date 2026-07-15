package com.stratuslite.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class StratusApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndPlacesWorkloadThroughHttpApi() throws Exception {
        mockMvc.perform(get("/api/cells"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value("cell-use1-a"));

        MvcResult createResult = mockMvc.perform(post("/api/workloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-alpha",
                                  "region": "us-east",
                                  "tier": "STANDARD",
                                  "demand": {
                                    "cpuCores": 2,
                                    "memoryGb": 4,
                                    "storageGb": 50,
                                    "iops": 1000
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.state").value("REQUESTED"))
                .andReturn();

        String workloadId = read(createResult).get("id").asText();

        mockMvc.perform(post("/api/workloads/{workloadId}/place", workloadId)
                        .param("strategy", "LEAST_ALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workloadId").value(workloadId))
                .andExpect(jsonPath("$.selectedCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.strategy").value("LEAST_ALLOCATED"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.candidates", hasSize(2)));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workloadId))
                .andExpect(jsonPath("$[0].state").value("PLACED"))
                .andExpect(jsonPath("$[0].assignedCellId").value("cell-use1-a"));

        mockMvc.perform(get("/api/placements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workloadId").value(workloadId));
    }

    @Test
    void returnsConflictWhenNoCellCanHostWorkload() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/workloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-beta",
                                  "region": "us-east",
                                  "tier": "PREMIUM",
                                  "demand": {
                                    "cpuCores": 128,
                                    "memoryGb": 512,
                                    "storageGb": 10000,
                                    "iops": 200000
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String workloadId = read(createResult).get("id").asText();

        mockMvc.perform(post("/api/workloads/{workloadId}/place", workloadId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No active cell can host workload " + workloadId + " in region us-east"));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

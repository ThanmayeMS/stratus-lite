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

    @Test
    void loadSpikeCreatesIncidentAndRebalanceRecommendation() throws Exception {
        String workloadId = createAndPlaceStandardWorkloadOnCellUse1A();

        mockMvc.perform(post("/api/simulations/load-spike")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cellId": "cell-use1-a",
                                  "load": {
                                    "cpuCores": 8,
                                    "memoryGb": 32,
                                    "storageGb": 600,
                                    "iops": 12000
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.incident.type").value("CELL_OVERLOADED"))
                .andExpect(jsonPath("$.incident.severity").value("WARNING"));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("CELL_OVERLOADED"));

        mockMvc.perform(get("/api/rebalance/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].workloadId").value(workloadId))
                .andExpect(jsonPath("$[0].sourceCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].targetCellId").value("cell-use1-b"))
                .andExpect(jsonPath("$[0].reason").value("Source cell crossed the overload threshold; move workload to reduce hotspot risk"));
    }

    @Test
    void cellFailureDegradesWorkloadsAndCreatesRestoreRecommendation() throws Exception {
        String workloadId = createAndPlaceStandardWorkloadOnCellUse1A();

        mockMvc.perform(post("/api/simulations/cell-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cellId": "cell-use1-a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.cellStatus").value("DOWN"))
                .andExpect(jsonPath("$.affectedWorkloads").value(1))
                .andExpect(jsonPath("$.incident.type").value("CELL_DOWN"))
                .andExpect(jsonPath("$.incident.severity").value("CRITICAL"));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workloadId))
                .andExpect(jsonPath("$[0].state").value("DEGRADED"))
                .andExpect(jsonPath("$[0].assignedCellId").value("cell-use1-a"));

        mockMvc.perform(get("/api/rebalance/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].workloadId").value(workloadId))
                .andExpect(jsonPath("$[0].sourceCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].targetCellId").value("cell-use1-b"))
                .andExpect(jsonPath("$[0].reason").value("Source cell is DOWN; workload should be restored on a healthy cell"));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createAndPlaceStandardWorkloadOnCellUse1A() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/workloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-gamma",
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
                .andReturn();

        String workloadId = read(createResult).get("id").asText();
        mockMvc.perform(post("/api/workloads/{workloadId}/place", workloadId)
                        .param("strategy", "LEAST_ALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCellId").value("cell-use1-a"));
        return workloadId;
    }
}

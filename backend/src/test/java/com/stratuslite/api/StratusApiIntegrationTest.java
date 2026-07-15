package com.stratuslite.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
                .andExpect(jsonPath("$.candidates", hasSize(4)))
                .andExpect(jsonPath("$.candidates[0].cellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.candidates[0].eligible").value(true))
                .andExpect(jsonPath("$.candidates[0].projectedUtilizationPercent").value(notNullValue()))
                .andExpect(jsonPath("$.candidates[0].policySummary").value(notNullValue()))
                .andExpect(jsonPath("$.candidates[2].eligible").value(false))
                .andExpect(jsonPath("$.candidates[2].policySummary").value(notNullValue()));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workloadId))
                .andExpect(jsonPath("$[0].state").value("PLACED"))
                .andExpect(jsonPath("$[0].assignedCellId").value("cell-use1-a"));

        mockMvc.perform(get("/api/placements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workloadId").value(workloadId));

        mockMvc.perform(get("/api/insights/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.activeCells").value(3))
                .andExpect(jsonPath("$.recommendedMoves").value(0))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("WORKLOAD_CREATED", "PLACEMENT_CREATED")));
    }

    @Test
    void resetDemoClearsMutableStateAndRestoresSeedFleet() throws Exception {
        String workloadId = createAndPlaceStandardWorkloadOnCellUse1A();

        mockMvc.perform(post("/api/simulations/cell-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cellId": "cell-use1-a"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rebalance/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workloadId": "%s",
                                  "sourceCellId": "cell-use1-a",
                                  "targetCellId": "cell-use1-b"
                                }
                                """.formatted(workloadId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Demo state reset"));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/placements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/rebalance/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/cells"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].usedCapacity.cpuCores").value(4))
                .andExpect(jsonPath("$[1].id").value("cell-use1-b"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].usedCapacity.cpuCores").value(18))
                .andExpect(jsonPath("$[2].id").value("cell-use1-maint"))
                .andExpect(jsonPath("$[2].status").value("DRAINING"))
                .andExpect(jsonPath("$[3].id").value("cell-usw2-a"))
                .andExpect(jsonPath("$[3].status").value("ACTIVE"));

        mockMvc.perform(get("/api/insights/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.downCells").value(0))
                .andExpect(jsonPath("$.totalWorkloads").value(0))
                .andExpect(jsonPath("$.recommendedMoves").value(0));
    }

    @Test
    void smokeScenarioRunsEndToEndFromResetState() throws Exception {
        mockMvc.perform(post("/api/admin/reset"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cells"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

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
                .andExpect(jsonPath("$.affectedWorkloads").value(1));

        MvcResult recommendationsResult = mockMvc.perform(get("/api/rebalance/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].workloadId").value(workloadId))
                .andExpect(jsonPath("$[0].sourceCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].targetCellId").value("cell-use1-b"))
                .andReturn();

        String targetCellId = read(recommendationsResult).get(0).get("targetCellId").asText();
        MvcResult executeResult = mockMvc.perform(post("/api/rebalance/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workloadId": "%s",
                                  "sourceCellId": "cell-use1-a",
                                  "targetCellId": "%s"
                                }
                                """.formatted(workloadId, targetCellId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workloadId").value(workloadId))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String executionId = read(executeResult).get("executionId").asText();

        mockMvc.perform(get("/api/rebalance/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(executionId))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/insights/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.downCells").value(1))
                .andExpect(jsonPath("$.recommendedMoves").value(0));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "WORKLOAD_CREATED",
                        "PLACEMENT_CREATED",
                        "CELL_FAILURE_SIMULATED",
                        "REBALANCE_EXECUTED"
                )));
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
                .andExpect(jsonPath("$.incident.severity").value("WARNING"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()));

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
                .andExpect(jsonPath("$[0].reason").value("Source cell crossed the overload threshold; move workload to reduce hotspot risk"))
                .andExpect(jsonPath("$[0].explanation").value(notNullValue()))
                .andExpect(jsonPath("$[0].operatorAction").value(notNullValue()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "WORKLOAD_CREATED",
                        "PLACEMENT_CREATED",
                        "LOAD_SPIKE_SIMULATED"
                )));
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
                .andExpect(jsonPath("$.incident.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()));

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
                .andExpect(jsonPath("$[0].reason").value("Source cell is DOWN; workload should be restored on a healthy cell"))
                .andExpect(jsonPath("$[0].explanation").value(notNullValue()))
                .andExpect(jsonPath("$[0].operatorAction").value(notNullValue()));

        mockMvc.perform(get("/api/insights/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.downCells").value(1))
                .andExpect(jsonPath("$.degradedWorkloads").value(1))
                .andExpect(jsonPath("$.recommendedMoves").value(1))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()));
    }

    @Test
    void executesRebalanceRecommendationAndMovesCapacity() throws Exception {
        String workloadId = createAndPlaceStandardWorkloadOnCellUse1A();

        mockMvc.perform(post("/api/simulations/cell-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cellId": "cell-use1-a"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rebalance/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workloadId": "%s",
                                  "sourceCellId": "cell-use1-a",
                                  "targetCellId": "cell-use1-b"
                                }
                                """.formatted(workloadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workloadId").value(workloadId))
                .andExpect(jsonPath("$.sourceCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.targetCellId").value("cell-use1-b"))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()))
                .andExpect(jsonPath("$.executionId", notNullValue()));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workloadId))
                .andExpect(jsonPath("$[0].state").value("RUNNING"))
                .andExpect(jsonPath("$[0].assignedCellId").value("cell-use1-b"));

        mockMvc.perform(get("/api/cells"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].usedCapacity.cpuCores").value(4))
                .andExpect(jsonPath("$[1].id").value("cell-use1-b"))
                .andExpect(jsonPath("$[1].usedCapacity.cpuCores").value(20));

        mockMvc.perform(get("/api/rebalance/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/rebalance/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].workloadId").value(workloadId))
                .andExpect(jsonPath("$[0].sourceCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].targetCellId").value("cell-use1-b"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].explanation").value(notNullValue()))
                .andExpect(jsonPath("$[0].operatorAction").value(notNullValue()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "WORKLOAD_CREATED",
                        "PLACEMENT_CREATED",
                        "CELL_FAILURE_SIMULATED",
                        "REBALANCE_EXECUTED"
                )));
    }

    @Test
    void rollsBackRebalanceExecutionWhenSourceCellIsHealthy() throws Exception {
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
                .andExpect(status().isOk());

        MvcResult executeResult = mockMvc.perform(post("/api/rebalance/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workloadId": "%s",
                                  "sourceCellId": "cell-use1-a",
                                  "targetCellId": "cell-use1-b"
                                }
                                """.formatted(workloadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String executionId = read(executeResult).get("executionId").asText();

        mockMvc.perform(post("/api/rebalance/executions/{executionId}/rollback", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId))
                .andExpect(jsonPath("$.workloadId").value(workloadId))
                .andExpect(jsonPath("$.sourceCellId").value("cell-use1-b"))
                .andExpect(jsonPath("$.targetCellId").value("cell-use1-a"))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()))
                .andExpect(jsonPath("$.operatorAction").value(notNullValue()));

        mockMvc.perform(get("/api/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workloadId))
                .andExpect(jsonPath("$[0].state").value("RUNNING"))
                .andExpect(jsonPath("$[0].assignedCellId").value("cell-use1-a"));

        mockMvc.perform(get("/api/cells"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cell-use1-a"))
                .andExpect(jsonPath("$[0].usedCapacity.cpuCores").value(14))
                .andExpect(jsonPath("$[1].id").value("cell-use1-b"))
                .andExpect(jsonPath("$[1].usedCapacity.cpuCores").value(18));

        mockMvc.perform(get("/api/rebalance/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(executionId))
                .andExpect(jsonPath("$[0].status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$[0].rolledBackAt", notNullValue()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "WORKLOAD_CREATED",
                        "PLACEMENT_CREATED",
                        "LOAD_SPIKE_SIMULATED",
                        "REBALANCE_EXECUTED",
                        "REBALANCE_ROLLED_BACK"
                )));
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

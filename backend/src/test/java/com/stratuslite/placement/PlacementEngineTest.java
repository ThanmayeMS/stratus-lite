package com.stratuslite.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.CellStatus;
import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import com.stratuslite.workload.WorkloadRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacementEngineTest {

    private final PlacementEngine placementEngine = new PlacementEngine();

    @Test
    void bestFitChoosesTheTightestFeasibleCell() {
        WorkloadRequest workload = workload(new ResourceVector(2, 4, 20, 500));
        List<Cell> cells = List.of(
                cell("cell-a", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(1, 2, 10, 200)),
                cell("cell-b", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(5, 10, 60, 1_200)),
                cell("cell-c", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(0, 0, 0, 0))
        );

        PlacementDecision decision = placementEngine.place(workload, cells, PlacementStrategy.BEST_FIT);

        assertThat(decision.selectedCell().id()).isEqualTo("cell-b");
        assertThat(decision.explanation()).contains("cell-b", "BEST_FIT");
    }

    @Test
    void leastAllocatedChoosesTheCellWithMostHeadroom() {
        WorkloadRequest workload = workload(new ResourceVector(2, 4, 20, 500));
        List<Cell> cells = List.of(
                cell("cell-a", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(1, 2, 10, 200)),
                cell("cell-b", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(5, 10, 60, 1_200)),
                cell("cell-c", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(0, 0, 0, 0))
        );

        PlacementDecision decision = placementEngine.place(workload, cells, PlacementStrategy.LEAST_ALLOCATED);

        assertThat(decision.selectedCell().id()).isEqualTo("cell-c");
    }

    @Test
    void filtersCellsByRegionTierStatusAndCapacity() {
        WorkloadRequest workload = new WorkloadRequest(
                "workload-1",
                "tenant-1",
                "us-east",
                ServiceTier.PREMIUM,
                new ResourceVector(4, 8, 40, 1_000)
        );
        List<Cell> cells = List.of(
                new Cell("wrong-region", "eu-west", ServiceTier.PREMIUM, CellStatus.ACTIVE,
                        new ResourceVector(16, 32, 500, 10_000), new ResourceVector(0, 0, 0, 0)),
                new Cell("wrong-tier", "us-east", ServiceTier.STANDARD, CellStatus.ACTIVE,
                        new ResourceVector(16, 32, 500, 10_000), new ResourceVector(0, 0, 0, 0)),
                new Cell("down", "us-east", ServiceTier.PREMIUM, CellStatus.DOWN,
                        new ResourceVector(16, 32, 500, 10_000), new ResourceVector(0, 0, 0, 0)),
                new Cell("too-small", "us-east", ServiceTier.PREMIUM, CellStatus.ACTIVE,
                        new ResourceVector(2, 4, 20, 500), new ResourceVector(0, 0, 0, 0)),
                new Cell("match", "us-east", ServiceTier.PREMIUM, CellStatus.ACTIVE,
                        new ResourceVector(16, 32, 500, 10_000), new ResourceVector(0, 0, 0, 0))
        );

        PlacementDecision decision = placementEngine.place(workload, cells, PlacementStrategy.BEST_FIT);

        assertThat(decision.selectedCell().id()).isEqualTo("match");
        assertThat(decision.candidates()).extracting(candidate -> candidate.cell().id())
                .containsExactly("match");
    }

    @Test
    void throwsWhenNoCellCanHostTheWorkload() {
        WorkloadRequest workload = workload(new ResourceVector(32, 64, 1_000, 20_000));
        List<Cell> cells = List.of(
                cell("cell-a", new ResourceVector(8, 16, 100, 2_000), new ResourceVector(0, 0, 0, 0))
        );

        assertThatThrownBy(() -> placementEngine.place(workload, cells, PlacementStrategy.BEST_FIT))
                .isInstanceOf(NoPlacementFoundException.class)
                .hasMessageContaining("No active cell can host workload");
    }

    private static WorkloadRequest workload(ResourceVector demand) {
        return new WorkloadRequest("workload-1", "tenant-1", "us-east", ServiceTier.STANDARD, demand);
    }

    private static Cell cell(String id, ResourceVector total, ResourceVector used) {
        return new Cell(id, "us-east", ServiceTier.STANDARD, CellStatus.ACTIVE, total, used);
    }
}


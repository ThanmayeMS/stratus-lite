package com.stratuslite.fleet;

import com.stratuslite.common.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FleetService {

    private final Map<String, Cell> cells = new LinkedHashMap<>();

    public FleetService() {
        seedFleet();
    }

    public synchronized List<Cell> listCells() {
        return cells.values().stream()
                .sorted(Comparator.comparing(Cell::id))
                .toList();
    }

    public synchronized Cell getCell(String cellId) {
        Cell cell = cells.get(cellId);
        if (cell == null) {
            throw new ResourceNotFoundException("Cell %s was not found".formatted(cellId));
        }
        return cell;
    }

    public synchronized Cell reserveCapacity(String cellId, ResourceVector demand) {
        Cell reserved = getCell(cellId).reserve(demand);
        cells.put(cellId, reserved);
        return reserved;
    }

    public synchronized Cell applyLoadSpike(String cellId, ResourceVector load) {
        Cell spiked = getCell(cellId).applyLoad(load);
        cells.put(cellId, spiked);
        return spiked;
    }

    public synchronized Cell markDown(String cellId) {
        Cell down = getCell(cellId).withStatus(CellStatus.DOWN);
        cells.put(cellId, down);
        return down;
    }

    public synchronized List<Cell> overloadedCells(double utilizationThreshold) {
        return cells.values().stream()
                .filter(cell -> cell.status() == CellStatus.ACTIVE)
                .filter(cell -> cell.isOverloaded(utilizationThreshold))
                .sorted(Comparator.comparing(Cell::id))
                .toList();
    }

    public synchronized List<Cell> activeCellsSnapshot() {
        return new ArrayList<>(cells.values());
    }

    private void seedFleet() {
        add(new Cell(
                "cell-use1-a",
                "us-east",
                ServiceTier.STANDARD,
                CellStatus.ACTIVE,
                new ResourceVector(16, 64, 1_000, 20_000),
                new ResourceVector(4, 16, 250, 4_000)
        ));
        add(new Cell(
                "cell-use1-b",
                "us-east",
                ServiceTier.PREMIUM,
                CellStatus.ACTIVE,
                new ResourceVector(32, 128, 2_000, 50_000),
                new ResourceVector(18, 72, 1_100, 28_000)
        ));
        add(new Cell(
                "cell-usw2-a",
                "us-west",
                ServiceTier.STANDARD,
                CellStatus.ACTIVE,
                new ResourceVector(16, 64, 1_000, 20_000),
                new ResourceVector(3, 12, 200, 3_000)
        ));
        add(new Cell(
                "cell-use1-maint",
                "us-east",
                ServiceTier.PREMIUM,
                CellStatus.DRAINING,
                new ResourceVector(32, 128, 2_000, 50_000),
                new ResourceVector(10, 32, 700, 12_000)
        ));
    }

    private void add(Cell cell) {
        cells.put(cell.id(), cell);
    }
}

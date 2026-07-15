package com.stratuslite.fleet;

import com.stratuslite.common.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetService {

    private final CellRepository cellRepository;

    public FleetService(CellRepository cellRepository) {
        this.cellRepository = cellRepository;
    }

    @PostConstruct
    void seedFleetIfEmpty() {
        if (cellRepository.count() > 0) {
            return;
        }
        seedFleet();
    }

    @Transactional(readOnly = true)
    public synchronized List<Cell> listCells() {
        return cellRepository.findAll();
    }

    @Transactional(readOnly = true)
    public synchronized Cell getCell(String cellId) {
        return cellRepository.findById(cellId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cell %s was not found".formatted(cellId)
                ));
    }

    @Transactional
    public synchronized Cell reserveCapacity(String cellId, ResourceVector demand) {
        Cell reserved = getCell(cellId).reserve(demand);
        cellRepository.save(reserved);
        return reserved;
    }

    @Transactional
    public synchronized Cell releaseCapacity(String cellId, ResourceVector demand) {
        Cell released = getCell(cellId).release(demand);
        cellRepository.save(released);
        return released;
    }

    @Transactional
    public synchronized Cell applyLoadSpike(String cellId, ResourceVector load) {
        Cell spiked = getCell(cellId).applyLoad(load);
        cellRepository.save(spiked);
        return spiked;
    }

    @Transactional
    public synchronized Cell markDown(String cellId) {
        Cell down = getCell(cellId).withStatus(CellStatus.DOWN);
        cellRepository.save(down);
        return down;
    }

    @Transactional(readOnly = true)
    public synchronized List<Cell> overloadedCells(double utilizationThreshold) {
        return cellRepository.findAll().stream()
                .filter(cell -> cell.status() == CellStatus.ACTIVE)
                .filter(cell -> cell.isOverloaded(utilizationThreshold))
                .toList();
    }

    @Transactional(readOnly = true)
    public synchronized List<Cell> activeCellsSnapshot() {
        return new ArrayList<>(cellRepository.findAll());
    }

    @Transactional
    public synchronized void resetFleet() {
        cellRepository.deleteAll();
        seedFleet();
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
        cellRepository.save(cell);
    }
}

package com.stratuslite.placement;

import com.stratuslite.fleet.Cell;

public record CandidateScore(
        Cell cell,
        boolean eligible,
        double score,
        double projectedUtilizationPercent,
        String policySummary,
        String reason
) implements Comparable<CandidateScore> {

    @Override
    public int compareTo(CandidateScore other) {
        int byEligibility = Boolean.compare(other.eligible, eligible);
        if (byEligibility != 0) {
            return byEligibility;
        }
        int byScore = Double.compare(other.score, score);
        if (byScore != 0) {
            return byScore;
        }
        return cell.id().compareTo(other.cell.id());
    }
}

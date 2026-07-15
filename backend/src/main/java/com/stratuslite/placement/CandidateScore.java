package com.stratuslite.placement;

import com.stratuslite.fleet.Cell;

public record CandidateScore(
        Cell cell,
        double score,
        String reason
) implements Comparable<CandidateScore> {

    @Override
    public int compareTo(CandidateScore other) {
        int byScore = Double.compare(other.score, score);
        if (byScore != 0) {
            return byScore;
        }
        return cell.id().compareTo(other.cell.id());
    }
}


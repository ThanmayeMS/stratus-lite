package com.stratuslite.placement.api;

import com.stratuslite.placement.CandidateScore;

public record CandidateScoreResponse(
        String cellId,
        boolean eligible,
        double score,
        double projectedUtilizationPercent,
        String policySummary,
        String reason
) {

    public static CandidateScoreResponse from(CandidateScore candidateScore) {
        return new CandidateScoreResponse(
                candidateScore.cell().id(),
                candidateScore.eligible(),
                candidateScore.score(),
                candidateScore.projectedUtilizationPercent(),
                candidateScore.policySummary(),
                candidateScore.reason()
        );
    }
}

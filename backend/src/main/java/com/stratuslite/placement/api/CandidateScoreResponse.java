package com.stratuslite.placement.api;

import com.stratuslite.placement.CandidateScore;

public record CandidateScoreResponse(
        String cellId,
        double score,
        String reason
) {

    public static CandidateScoreResponse from(CandidateScore candidateScore) {
        return new CandidateScoreResponse(
                candidateScore.cell().id(),
                candidateScore.score(),
                candidateScore.reason()
        );
    }
}


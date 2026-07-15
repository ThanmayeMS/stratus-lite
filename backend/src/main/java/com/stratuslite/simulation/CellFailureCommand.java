package com.stratuslite.simulation;

import jakarta.validation.constraints.NotBlank;

public record CellFailureCommand(
        @NotBlank String cellId
) {
}


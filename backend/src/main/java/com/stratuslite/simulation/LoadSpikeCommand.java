package com.stratuslite.simulation;

import com.stratuslite.fleet.ResourceVector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoadSpikeCommand(
        @NotBlank String cellId,
        @Valid @NotNull ResourceVector load
) {
}


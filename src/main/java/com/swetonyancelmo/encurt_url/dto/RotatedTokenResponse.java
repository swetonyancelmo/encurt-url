package com.swetonyancelmo.encurt_url.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RotatedTokenResponse(
        @Schema(example = "aB3dE9x") String code,
        @Schema(description = "Novo token. O anterior deixou de valer. Aparece somente nesta resposta.",
                example = "q3J0m2v8K1xYw9Zt5bN7cD4eF6gH8iJ0kL2mN4oP6qR")
        String managementToken
) {
}

package com.centralizesys.model.dto.external;

/**
 * Immutable DTO representing an active consolidated product for external B2B consumers.
 *
 * <p>To adhere to the Principle of Data Minimization (OWASP) and prevent commercial
 * intelligence leakage (e.g., third parties tracking sales velocity or turnover via
 * integer stock diffs), raw stock quantities are strictly withheld. The client is only
 * exposed to boolean availability.
 *
 * @param codigo Product article code / SKU family identifier.
 * @param descripcion Product family description.
 * @param disponible True if net aggregated physical stock across all variants is positive (> 0).
 */
public record CatalogProductDto(
        String codigo,
        String descripcion,
        boolean disponible
) {}

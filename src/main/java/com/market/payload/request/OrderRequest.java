package com.market.payload.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Map;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {
    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double locationLatitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double locationLongitude;

    /** Item id to quantity. */
    @NotEmpty
    private Map<@NotNull Long, @NotNull @Positive Integer> items;
}

package com.market.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemResponse {
    private Long itemId;
    private String itemName;
    private Integer itemQty;
    private BigDecimal unitPrice;
    /** unitPrice * itemQty */
    private BigDecimal amount;
}

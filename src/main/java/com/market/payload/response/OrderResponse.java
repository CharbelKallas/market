package com.market.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
    private Long id;
    private Long userId;
    private Date orderDate;
    private Double locationLatitude;
    private Double locationLongitude;
    private List<OrderItemResponse> orderItems;
    private BigDecimal deliveryCharge;
    /** Sum of the order items plus the delivery charge. */
    private BigDecimal total;
}

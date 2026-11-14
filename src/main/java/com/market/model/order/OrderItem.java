package com.market.model.order;

import com.market.model.item.ItemAmount;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"order_item\"")
public class OrderItem {

    @Id
    @Column(name = "order_item_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_qty", nullable = false)
    private Integer itemQty;

    /** The exact price the item had when ordered. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_amount_id", nullable = false)
    private ItemAmount itemAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    public BigDecimal getLineTotal() {
        return itemAmount.getAmount().multiply(BigDecimal.valueOf(itemQty));
    }

}

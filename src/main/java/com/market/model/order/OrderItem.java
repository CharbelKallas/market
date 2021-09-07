package com.market.model.order;

import com.market.model.item.ItemAmount;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;

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

    @Column(name = "item_qty")
    private Integer itemQty;

    @ManyToOne
    @JoinColumn(name = "item_amount_id")
    private ItemAmount itemAmount;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

}

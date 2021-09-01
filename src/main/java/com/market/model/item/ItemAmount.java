package com.market.model.item;

import com.market.model.order.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"item_amount\"")
public class ItemAmount {

    @Id
    @Column(name = "item_amount_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_date")
    private LocalDateTime activeDate;

    private double amount;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    @OneToMany(mappedBy = "itemAmount", cascade = CascadeType.ALL)
    private Set<OrderItem> orderItems;

}

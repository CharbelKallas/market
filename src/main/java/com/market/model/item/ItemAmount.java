package com.market.model.item;

import com.market.model.order.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * One price of an item. Prices are kept as history; the newest active one applies.
 */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"item_amount\"")
@EntityListeners(AuditingEntityListener.class)
public class ItemAmount {

    @Id
    @Column(name = "item_amount_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_date", nullable = false)
    @CreatedDate
    private LocalDateTime activeDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @OneToMany(mappedBy = "itemAmount")
    private Set<OrderItem> orderItems;

}

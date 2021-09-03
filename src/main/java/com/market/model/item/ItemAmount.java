package com.market.model.item;

import com.market.model.order.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

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

    @Column(name = "active_date")
    @CreatedDate
    private LocalDateTime activeDate;

    private double amount;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    @OneToMany(mappedBy = "itemAmount", cascade = CascadeType.ALL)
    private Set<OrderItem> orderItems;

}

package com.market.model.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"item\"")
public class Item {

    @Id
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false, unique = true)
    private String itemName;

    // bytea, not @Lob: on PostgreSQL @Lob maps to a large-object OID that can't be read outside a transaction.
    @Column(name = "image", columnDefinition = "bytea")
    private byte[] image;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "content_type")
    private String contentType;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    private Set<ItemAmount> itemAmounts = new HashSet<>();

    /**
     * The price in effect now: the most recent one whose active date is not in the future.
     */
    public Optional<ItemAmount> getCurrentAmount() {
        LocalDateTime now = LocalDateTime.now();
        return itemAmounts.stream()
                .filter(amount -> amount.getActiveDate() != null && !amount.getActiveDate().isAfter(now))
                .max(Comparator.comparing(ItemAmount::getActiveDate));
    }

    public boolean hasImage() {
        return image != null && image.length > 0;
    }
}

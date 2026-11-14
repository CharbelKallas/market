package com.market.model.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
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

    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** Lower-case item name; its unique constraint makes "Apple" and "apple" the same item even under races. */
    @Column(name = "item_key", nullable = false, unique = true)
    private String itemKey;

    public Item setItemName(String itemName) {
        this.itemName = itemName;
        this.itemKey = itemName == null ? null : itemName.toLowerCase(Locale.ROOT);
        return this;
    }

    /** Lazy: the FK lives on this side, so a proxy stands in and image bytes load only when read. */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "item_image_id")
    private ItemImage image;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    @BatchSize(size = 100)
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
        return image != null;
    }
}

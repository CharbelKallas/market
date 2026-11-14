package com.market.model.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;

/**
 * An item's image, kept apart from {@link Item} so listing items never loads image bytes.
 */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"item_image\"")
public class ItemImage {

    @Id
    @Column(name = "item_image_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain byte[] (bytea), not @Lob: on PostgreSQL @Lob maps to a large-object OID that can't be read outside a
    // transaction.
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "content_type", nullable = false)
    private String contentType;
}

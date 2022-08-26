package com.market.model.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;

import javax.persistence.*;
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

    @Column(name = "item_name")
    private String itemName;

    @Lob
    @Column(name = "image")
    private byte[] image;

    @Column(name = "image_name")
    private String imageName;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    private Set<ItemAmount> itemAmounts;

}

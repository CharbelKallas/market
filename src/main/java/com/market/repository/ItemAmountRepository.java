package com.market.repository;

import com.market.model.item.ItemAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ItemAmountRepository extends JpaRepository<ItemAmount, Long> {
    List<ItemAmount> findAllByItemIdInAndActiveDateBeforeOrderByActiveDateDesc(Collection<Long> item_id, LocalDateTime activeDate);
}

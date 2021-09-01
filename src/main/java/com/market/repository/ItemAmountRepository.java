package com.market.repository;

import com.market.model.item.ItemAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemAmountRepository extends JpaRepository<ItemAmount, Long> {
}

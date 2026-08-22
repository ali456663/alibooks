package se.cloudshop.card;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardPurchaseRepository extends JpaRepository<CardPurchase, Long> {
  List<CardPurchase> findAllByOrderByPurchaseDateDescIdDesc();
}

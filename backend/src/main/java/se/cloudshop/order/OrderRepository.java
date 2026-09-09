package se.cloudshop.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.customer.Customer;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomer(Customer customer);

  // Lock the row before loading eager payment/history collections and checking balances.
  @Transactional(propagation = Propagation.MANDATORY)
  @Query(value = "SELECT id FROM customer_orders WHERE id = :id FOR UPDATE", nativeQuery = true)
  Long lockById(@Param("id") Long id);

  @Transactional
  @Modifying
  @Query("update Order o set o.stripeCheckoutSessionId = :sessionId where o.id = :id")
  int updateStripeCheckoutSessionId(@Param("id") Long id, @Param("sessionId") String sessionId);
}

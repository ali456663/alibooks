package se.cloudshop.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import se.cloudshop.customer.Customer;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomer(Customer customer);
}

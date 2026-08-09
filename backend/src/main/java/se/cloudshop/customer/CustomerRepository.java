package se.cloudshop.customer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

  boolean existsByPersonalNumber(String personalNumber);

  boolean existsByPersonalNumberAndIdNot(String personalNumber, Long id);
}

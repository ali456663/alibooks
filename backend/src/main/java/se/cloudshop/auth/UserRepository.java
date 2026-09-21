package se.cloudshop.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @Modifying
  @Query(value = "LOCK TABLE app_users IN EXCLUSIVE MODE", nativeQuery = true)
  void lockRegistrationTable();
}

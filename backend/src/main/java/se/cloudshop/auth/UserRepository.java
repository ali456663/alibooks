package se.cloudshop.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.email = :email")
  Optional<User> findByEmailForUpdate(@Param("email") String email);

  @Modifying
  @Query(value = "LOCK TABLE app_users IN EXCLUSIVE MODE", nativeQuery = true)
  void lockRegistrationTable();
}

package se.cloudshop.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollSnapshotRepository extends JpaRepository<PayrollSnapshot, Long> {
}

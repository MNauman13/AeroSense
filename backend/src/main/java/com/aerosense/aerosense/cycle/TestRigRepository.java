package com.aerosense.aerosense.cycle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRigRepository extends JpaRepository<TestRigEntity, UUID> {

  List<TestRigEntity> findAllByOrderByRigCodeAsc();
}

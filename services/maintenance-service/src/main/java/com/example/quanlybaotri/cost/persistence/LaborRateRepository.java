package com.example.quanlybaotri.cost.persistence;
import com.example.quanlybaotri.cost.domain.LaborRate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LaborRateRepository extends JpaRepository<LaborRate,UUID> {
    List<LaborRate> findByTechnicianIdOrderByEffectiveFromDesc(UUID technicianId);
    List<LaborRate> findByTechnicianIdIsNullOrderByEffectiveFromDesc();
}

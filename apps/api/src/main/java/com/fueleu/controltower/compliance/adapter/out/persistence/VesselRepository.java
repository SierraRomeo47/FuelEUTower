package com.fueleu.controltower.compliance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.fueleu.controltower.compliance.domain.Vessel;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface VesselRepository extends JpaRepository<Vessel, UUID> {
    Optional<Vessel> findByImoNumber(String imoNumber);
}

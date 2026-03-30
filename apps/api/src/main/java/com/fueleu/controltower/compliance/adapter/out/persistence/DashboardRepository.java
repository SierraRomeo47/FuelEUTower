package com.fueleu.controltower.compliance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.fueleu.controltower.compliance.domain.Vessel;
import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardRepository extends JpaRepository<Vessel, UUID> {

    @Query(value = "SELECT v.imo_number as id, v.name as name, v.vessel_type as type, " +
                   "COALESCE(c.icb_value, 0) as icb, " +
                   "CASE WHEN c.icb_value < 0 THEN 'Deficit' ELSE 'Compliant' END as status " +
                   "FROM vessel v " +
                   "LEFT JOIN vessel_year vy ON v.id = vy.vessel_id " +
                   "LEFT JOIN compliance_calculation c ON vy.id = c.vessel_year_id", nativeQuery = true)
    List<DashboardVesselProjection> findHighRiskVessels();

    @Query(value = "SELECT COALESCE(SUM(c.icb_value), 0) as totalIcb, " +
                   "COALESCE(SUM(c.acb_value), 0) as totalAcb, " +
                   "COALESCE(SUM(c.borrowing_cap), 0) as totalBorrowingCap " +
                   "FROM compliance_calculation c", nativeQuery = true)
    DashboardKpiProjection getGlobalKpis();

    interface DashboardVesselProjection {
        String getId();
        String getName();
        String getType();
        Double getIcb();
        String getStatus();
    }

    interface DashboardKpiProjection {
        Double getTotalIcb();
        Double getTotalAcb();
        Double getTotalBorrowingCap();
    }
}

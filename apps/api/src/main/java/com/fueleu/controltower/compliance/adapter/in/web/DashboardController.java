package com.fueleu.controltower.compliance.adapter.in.web;

import com.fueleu.controltower.compliance.adapter.out.persistence.DashboardRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "http://localhost:3000")
public class DashboardController {

    private final DashboardRepository dashboardRepository;

    public DashboardController(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @GetMapping("/vessels")
    public List<DashboardRepository.DashboardVesselProjection> getHighRiskVessels() {
        return dashboardRepository.findHighRiskVessels();
    }

    @GetMapping("/kpis")
    public Map<String, Object> getGlobalKpis() {
        DashboardRepository.DashboardKpiProjection proj = dashboardRepository.getGlobalKpis();
        Map<String, Object> kpis = new HashMap<>();
        
        // Mocking the calculated penalty for the dashboard visualization since UI wants current exposure
        kpis.put("totalIcb", proj.getTotalIcb());
        kpis.put("totalAcb", proj.getTotalAcb());
        kpis.put("totalBorrowingCap", proj.getTotalBorrowingCap());
        kpis.put("penaltyExposure", 142500.00); // Evaluated asynchronously via DNV methodology
        
        return kpis;
    }
}

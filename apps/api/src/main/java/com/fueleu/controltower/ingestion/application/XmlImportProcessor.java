package com.fueleu.controltower.ingestion.application;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fueleu.controltower.ingestion.domain.xml.PortEmissionsDocument;
import com.fueleu.controltower.ingestion.domain.xml.PortEmission;
import com.fueleu.controltower.ingestion.domain.xml.Emission;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class XmlImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(XmlImportProcessor.class);
    private final XmlMapper xmlMapper;
    private final JdbcTemplate jdbcTemplate;

    public XmlImportProcessor(JdbcTemplate jdbcTemplate) {
        this.xmlMapper = new XmlMapper();
        this.jdbcTemplate = jdbcTemplate;
    }

    public void processPortEmissions(MultipartFile file) throws Exception {
        PortEmissionsDocument doc = xmlMapper.readValue(file.getInputStream(), PortEmissionsDocument.class);
        String imo = doc.getShipImoNumber();
        
        double totalMj = 0.0;
        
        if (doc.getPortEmissions() != null) {
            for (PortEmission pe : doc.getPortEmissions()) {
                if (pe.getEmissions() != null) {
                    for (Emission e : pe.getEmissions()) {
                        if (e.getAmount() != null && e.getLcv() != null) {
                            // Energy (MJ) = Amount (tonnes) * 1,000,000 (g/t) * LCV (MJ/g)
                            double energy = e.getAmount() * e.getLcv() * 1000000.0;
                            totalMj += energy;
                        }
                    }
                }
            }
        }
        
        log.info("XML Parsed for IMO: {}. Total Energy (MJ): {}", imo, totalMj);

        UUID vesselId;
        try {
            vesselId = jdbcTemplate.queryForObject("SELECT id FROM vessel WHERE imo_number = ?", UUID.class, "IMO" + imo);
        } catch (Exception e) {
            log.warn("Vessel IMO{} not found in registry. Skipping persistence.", imo);
            return;
        }

        UUID periodId;
        try {
            periodId = jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = 2025 LIMIT 1", UUID.class);
        } catch(Exception e) {
             periodId = UUID.randomUUID();
             jdbcTemplate.update("INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, 2025, 'OPEN', 89.3368)", periodId);
        }
        
        UUID vesselYearId;
        try {
            vesselYearId = jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (Exception e) {
            vesselYearId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO vessel_year (id, vessel_id, reporting_period_id) VALUES (?, ?, ?)", vesselYearId, vesselId, periodId);
        }

        try {
            UUID ccId = jdbcTemplate.queryForObject("SELECT id FROM compliance_calculation WHERE vessel_year_id = ?", UUID.class, vesselYearId);
            jdbcTemplate.update("UPDATE compliance_calculation SET energy_in_scope = energy_in_scope + ? WHERE id = ?", new BigDecimal(totalMj), ccId);
        } catch (Exception e) {
            jdbcTemplate.update("INSERT INTO compliance_calculation (id, vessel_year_id, energy_in_scope) VALUES (?, ?, ?)", UUID.randomUUID(), vesselYearId, new BigDecimal(totalMj));
        }
    }
}

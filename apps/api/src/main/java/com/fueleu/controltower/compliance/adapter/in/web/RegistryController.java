package com.fueleu.controltower.compliance.adapter.in.web;

import com.fueleu.controltower.compliance.adapter.out.persistence.VesselRepository;
import com.fueleu.controltower.compliance.domain.Vessel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registry")
@CrossOrigin(origins = "http://localhost:3000")
public class RegistryController {

    private final VesselRepository vesselRepository;

    public RegistryController(VesselRepository vesselRepository) {
        this.vesselRepository = vesselRepository;
    }

    @GetMapping("/vessels")
    public List<Vessel> getVessels() {
        return vesselRepository.findAll();
    }

    @PostMapping("/vessels")
    public ResponseEntity<Vessel> registerVessel(@RequestBody Vessel newVessel) {
        try {
            return ResponseEntity.ok(vesselRepository.save(newVessel));
        } catch (DataIntegrityViolationException ex) {
            // Most common cause in demos: imo_number unique constraint already exists.
            return vesselRepository.findByImoNumber(newVessel.getImoNumber())
                    .map(existing -> ResponseEntity.status(HttpStatus.CONFLICT).body(existing))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
        }
    }

    @PutMapping("/vessels/{vesselId}")
    public ResponseEntity<Vessel> updateVessel(@PathVariable UUID vesselId, @RequestBody Vessel update) {
        return vesselRepository.findById(vesselId)
                .map(existing -> {
                    existing.setImoNumber(update.getImoNumber());
                    existing.setName(update.getName());
                    existing.setVesselType(update.getVesselType());
                    existing.setIceClass(update.getIceClass());
                    existing.setBuildYear(update.getBuildYear());
                    existing.setFlagState(update.getFlagState());
                    try {
                        return ResponseEntity.ok(vesselRepository.save(existing));
                    } catch (DataIntegrityViolationException ex) {
                        return vesselRepository.findByImoNumber(update.getImoNumber())
                                .map(duplicate -> ResponseEntity.status(HttpStatus.CONFLICT).body(duplicate))
                                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

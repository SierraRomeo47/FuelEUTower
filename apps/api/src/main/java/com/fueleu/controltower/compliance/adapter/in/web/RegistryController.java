package com.fueleu.controltower.compliance.adapter.in.web;

import com.fueleu.controltower.compliance.adapter.out.persistence.VesselRepository;
import com.fueleu.controltower.compliance.domain.Vessel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}

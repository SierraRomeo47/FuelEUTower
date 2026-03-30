package com.fueleu.controltower.ingestion.adapter.in.web;

import com.fueleu.controltower.ingestion.domain.ImportBatch;
import com.fueleu.controltower.ingestion.application.ExcelImportProcessor;
import com.fueleu.controltower.ingestion.application.XmlImportProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
@CrossOrigin(origins = "http://localhost:3000")
public class ImportController {

    private final ExcelImportProcessor excelProcessor;
    private final XmlImportProcessor xmlProcessor;

    public ImportController(ExcelImportProcessor excelProcessor, XmlImportProcessor xmlProcessor) {
        this.excelProcessor = excelProcessor;
        this.xmlProcessor = xmlProcessor;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            String uploadedBy = "system-admin"; 
            
            // This endpoint currently runs a lightweight ingestion pass without persisting the batch entity.
            // Generate a deterministic tracking ID to return to the UI.
            UUID batchId = UUID.randomUUID();
            new ImportBatch(filename, file.getContentType(), uploadedBy);
            
            if (filename != null && filename.endsWith(".xlsx")) {
                var imos = excelProcessor.parseVesselMaster(file);
                return ResponseEntity.ok(Map.of("message", "Excel processed", "imosFound", imos.size(), "batchId", batchId));
            } else if (filename != null && filename.endsWith(".xml")) {
                xmlProcessor.processPortEmissions(file);
                return ResponseEntity.ok(Map.of("message", "XML processed", "batchId", batchId));
            }
            
            return ResponseEntity.badRequest().body("Unsupported file type");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}

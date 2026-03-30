package com.fueleu.controltower.ingestion.application;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelImportProcessor {

    public List<String> parseVesselMaster(MultipartFile file) throws Exception {
        List<String> imoNumbersFound = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = workbook.getSheetAt(0); // Assuming first sheet is Vessel Master
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                
                Cell imoCell = row.getCell(0); // Assuming IMO is column 0
                if (imoCell != null) {
                    imoNumbersFound.add(imoCell.getStringCellValue());
                }
            }
        }
        return imoNumbersFound;
    }
}

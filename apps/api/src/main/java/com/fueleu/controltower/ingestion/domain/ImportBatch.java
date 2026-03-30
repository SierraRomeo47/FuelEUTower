package com.fueleu.controltower.ingestion.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_batch")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportBatchStatus status;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ImportBatch() {}

    public ImportBatch(String filename, String fileType, String uploadedBy) {
        this.filename = filename;
        this.fileType = fileType;
        this.status = ImportBatchStatus.DRAFT;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public String getFileType() { return fileType; }
    public ImportBatchStatus getStatus() { return status; }
    public void setStatus(ImportBatchStatus status) { this.status = status; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

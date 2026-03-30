package com.fueleu.controltower.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "vessel")
public class Vessel {
    
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "imo_number")
    private String imoNumber;

    private String name;

    @Column(name = "vessel_type")
    private String vesselType;

    @Column(name = "ice_class")
    private String iceClass;

    @Column(name = "build_year")
    private Integer buildYear;

    @Column(name = "flag_state")
    private String flagState;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getImoNumber() { return imoNumber; }
    public void setImoNumber(String imoNumber) { this.imoNumber = imoNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVesselType() { return vesselType; }
    public void setVesselType(String vesselType) { this.vesselType = vesselType; }
    public String getIceClass() { return iceClass; }
    public void setIceClass(String iceClass) { this.iceClass = iceClass; }
    public Integer getBuildYear() { return buildYear; }
    public void setBuildYear(Integer buildYear) { this.buildYear = buildYear; }
    public String getFlagState() { return flagState; }
    public void setFlagState(String flagState) { this.flagState = flagState; }
}

package com.fueleu.controltower.ingestion.domain.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Emission {

    @JacksonXmlProperty(localName = "amount")
    private Double amount;
    
    @JacksonXmlProperty(localName = "lcv")
    private Double lcv;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getLcv() {
        return lcv;
    }

    public void setLcv(Double lcv) {
        this.lcv = lcv;
    }
}

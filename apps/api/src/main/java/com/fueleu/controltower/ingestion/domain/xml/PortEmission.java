package com.fueleu.controltower.ingestion.domain.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PortEmission {
    
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "emissions")
    private List<Emission> emissions;

    public List<Emission> getEmissions() {
        return emissions;
    }

    public void setEmissions(List<Emission> emissions) {
        this.emissions = emissions;
    }
}

package com.fueleu.controltower.ingestion.domain.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "portEmissions")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortEmissionsDocument {

    @JacksonXmlProperty(isAttribute = true, localName = "shipImoNumber")
    private String shipImoNumber;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "portEmission")
    private List<PortEmission> portEmissions;

    public String getShipImoNumber() {
        return shipImoNumber;
    }

    public void setShipImoNumber(String shipImoNumber) {
        this.shipImoNumber = shipImoNumber;
    }

    public List<PortEmission> getPortEmissions() {
        return portEmissions;
    }

    public void setPortEmissions(List<PortEmission> portEmissions) {
        this.portEmissions = portEmissions;
    }
}

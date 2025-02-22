package com.ea.model;

import lombok.Data;
import org.apache.commons.text.StringEscapeUtils;

@Data
public class LocationInfo {
    private final GeoLocation location;
    private final String personaName;

    public LocationInfo(GeoLocation location, String personaName) {
        this.location = location;
        this.personaName = personaName.replaceAll("\"", "");
    }
}
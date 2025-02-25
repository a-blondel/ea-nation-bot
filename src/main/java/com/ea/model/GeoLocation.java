package com.ea.model;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class GeoLocation {
    private double latitude;
    private double longitude;
    private String country;
}
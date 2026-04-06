package com.therapea.backend.dto;

public class StatDTO {
    public String iconKey;
    public String color;
    public String value;
    public String label;
    public String change;
    public boolean positive;

    public StatDTO(String iconKey, String color, String value, String label, String change, boolean positive) {
        this.iconKey = iconKey; this.color = color; this.value = value;
        this.label = label; this.change = change; this.positive = positive;
    }
}
package com.therapea.backend.dto;

public class ProgressDTO {
    public String label;
    public int value;
    public String cls;

    public ProgressDTO(String label, int value, String cls) {
        this.label = label; this.value = value; this.cls = cls;
    }
}

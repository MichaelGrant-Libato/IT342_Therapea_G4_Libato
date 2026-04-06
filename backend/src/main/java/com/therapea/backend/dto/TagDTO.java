package com.therapea.backend.dto;

import java.util.List;

public class TagDTO {
    public String label;
    public String cls;

    public TagDTO(String label, String cls) {
        this.label = label; this.cls = cls;
    }
}
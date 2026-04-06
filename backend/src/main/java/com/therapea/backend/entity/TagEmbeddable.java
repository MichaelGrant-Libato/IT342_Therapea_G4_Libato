package com.therapea.backend.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class TagEmbeddable {
    private String label;
    private String cssClass;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCssClass() { return cssClass; }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }
}
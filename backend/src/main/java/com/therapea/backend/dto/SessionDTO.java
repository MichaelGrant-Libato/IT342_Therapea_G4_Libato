package com.therapea.backend.dto;

import java.util.List;

public class SessionDTO {
    public String date;
    public String time;
    public String name;
    public String type;
    public List<TagDTO> tags;
    public boolean isToday;

    public SessionDTO(String date, String time, String name, String type, List<TagDTO> tags, boolean isToday) {
        this.date = date; this.time = time; this.name = name;
        this.type = type; this.tags = tags; this.isToday = isToday;
    }
}

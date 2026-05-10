package com.therapea.backend.features.users;

public class UserRegistrationDTO {
    private String fullName;
    private String email;
    private String password;
    private String role;
    private String availableSchedule;
    private String whatToExpect;

    private Boolean profileCompleted;

    private String profilePictureUrl;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvailableSchedule() { return availableSchedule; }
    public void setAvailableSchedule(String availableSchedule) { this.availableSchedule = availableSchedule; }

    public String getWhatToExpect() { return whatToExpect; }
    public void setWhatToExpect(String whatToExpect) { this.whatToExpect = whatToExpect; }

    public Boolean getProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(Boolean profileCompleted) { this.profileCompleted = profileCompleted; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
}
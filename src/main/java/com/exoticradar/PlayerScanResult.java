package com.exoticradar;

public class PlayerScanResult {
    public enum Status { PENDING, CHECKING, DONE, FAILED }

    public final String username;
    public String uuid = "unknown";
    public Status status = Status.PENDING;
    public boolean isExotic = false;
    public String exoticReason = "none";
    public String failReason = "";
    public long checkedAt = 0;

    // Full armor details for the player detail screen
    public ExoticDetector.ArmorScanResult armorDetail = null;
    // Raw member JSON stored for detailed view
    public com.google.gson.JsonObject rawMemberData = null;

    public PlayerScanResult(String username) {
        this.username = username;
    }
}

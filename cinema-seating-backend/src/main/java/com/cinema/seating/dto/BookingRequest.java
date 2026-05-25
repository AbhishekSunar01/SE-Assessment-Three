package com.cinema.seating.dto;

import java.util.List;

/**
 * Incoming request payload for a seat booking.
 * Deserialised from the REST API JSON body by the controller layer.
 *
 * For normal bookings: supply groupSize and seatTypePreference only.
 * For admin overrides: set adminOverride=true and supply the exact seat
 * references in overrideSeats (format: rowLetter + columnNumber, e.g. "G14").
 */
public class BookingRequest {

    /** Number of people in the group. Valid range: 1–7. */
    private int groupSize;

    /**
     * Requested seat zone: "REGULAR", "VIP", or "DISABILITY".
     * The algorithm maps this to SeatType; unknown values default to REGULAR.
     */
    private String seatTypePreference;

    /**
     * When true, all seating rules (scatter prevention, zone restrictions, etc.)
     * are bypassed and the seats in overrideSeats are assigned directly.
     */
    private boolean adminOverride;

    /**
     * Only used when adminOverride is true.
     * Each entry is a seat reference string such as "A3" or "G14".
     * Ignored for normal bookings.
     */
    private List<String> overrideSeats;

    public BookingRequest() {
    }

    public BookingRequest(int groupSize, String seatTypePreference,
                          boolean adminOverride, List<String> overrideSeats) {
        this.groupSize          = groupSize;
        this.seatTypePreference = seatTypePreference;
        this.adminOverride      = adminOverride;
        this.overrideSeats      = overrideSeats;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int groupSize) {
        this.groupSize = groupSize;
    }

    public String getSeatTypePreference() {
        return seatTypePreference;
    }

    public void setSeatTypePreference(String seatTypePreference) {
        this.seatTypePreference = seatTypePreference;
    }

    public boolean isAdminOverride() {
        return adminOverride;
    }

    public void setAdminOverride(boolean adminOverride) {
        this.adminOverride = adminOverride;
    }

    public List<String> getOverrideSeats() {
        return overrideSeats;
    }

    public void setOverrideSeats(List<String> overrideSeats) {
        this.overrideSeats = overrideSeats;
    }
}

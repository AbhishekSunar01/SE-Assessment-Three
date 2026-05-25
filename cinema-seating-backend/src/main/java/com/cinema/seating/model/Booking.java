package com.cinema.seating.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Records a confirmed booking after the seating algorithm has selected seats.
 * Once created, the assigned seats are immutable — cancellation produces a
 * new state change on the Seat objects themselves, not here.
 */
public class Booking {

    private final String bookingId;
    private final int groupSize;
    private final SeatType seatTypePreference;
    private final List<Seat> assignedSeats;

    public Booking(int groupSize, SeatType seatTypePreference, List<Seat> assignedSeats) {
        this.bookingId          = UUID.randomUUID().toString();
        this.groupSize          = groupSize;
        this.seatTypePreference = seatTypePreference;
        this.assignedSeats      = Collections.unmodifiableList(new ArrayList<>(assignedSeats));
    }

    /**
     * Returns a comma-separated list of seat references for display, e.g. "G12, G13, G14".
     */
    public String getAssignedSeatsSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < assignedSeats.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(assignedSeats.get(i).getSeatReference());
        }
        return sb.toString();
    }

    public String getBookingId() {
        return bookingId;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public SeatType getSeatTypePreference() {
        return seatTypePreference;
    }

    public List<Seat> getAssignedSeats() {
        return assignedSeats;
    }

    @Override
    public String toString() {
        return "Booking{id=" + bookingId
                + ", groupSize=" + groupSize
                + ", preference=" + seatTypePreference
                + ", seats=[" + getAssignedSeatsSummary() + "]}";
    }
}

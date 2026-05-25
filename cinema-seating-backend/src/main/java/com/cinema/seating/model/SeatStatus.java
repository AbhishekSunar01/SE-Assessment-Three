package com.cinema.seating.model;

/**
 * Represents the current occupancy state of a seat.
 * A seat's status changes over time as bookings are made or cancelled;
 * its SeatType is fixed at initialisation.
 */
public enum SeatStatus {
    AVAILABLE,
    BOOKED,
    BROKEN
}

package com.cinema.seating.model;

/**
 * Classifies what kind of seat a position represents.
 * BROKEN is assigned during initialisation and overrides any structural type.
 */
public enum SeatType {
    REGULAR,
    VIP,
    DISABILITY,
    BROKEN
}

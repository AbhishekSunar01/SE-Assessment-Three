package com.cinema.seating.model;

/**
 * Represents a single physical seat in the cinema hall.
 * A seat is uniquely identified by its row label and column number.
 * Its type is fixed at hall initialisation; its status changes as bookings occur.
 */
public class Seat {

    private final char rowLabel;
    private final int columnNumber;
    private SeatType seatType;
    private SeatStatus status;

    public Seat(char rowLabel, int columnNumber, SeatType seatType, SeatStatus status) {
        this.rowLabel = rowLabel;
        this.columnNumber = columnNumber;
        this.seatType = seatType;
        this.status = status;
    }

    /**
     * Returns true only when this seat can actually be assigned to a customer.
     * Broken and already-booked seats both return false.
     */
    public boolean isAvailable() {
        return this.status == SeatStatus.AVAILABLE;
    }

    /**
     * Returns a compact human-readable identifier, e.g. "G14".
     */
    public String getSeatReference() {
        return String.valueOf(rowLabel) + columnNumber;
    }

    public char getRowLabel() {
        return rowLabel;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getSeatReference() + "[" + seatType + "/" + status + "]";
    }
}

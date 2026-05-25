package com.cinema.seating.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents a single row in the cinema hall (e.g. row 'G').
 * Owns an ordered list of seats and provides block-finding logic
 * that the seating algorithm uses when evaluating candidate placements.
 */
public class Row {

    private final char rowLabel;
    private final List<Seat> seats;

    public Row(char rowLabel) {
        this.rowLabel = rowLabel;
        this.seats = new ArrayList<>();
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seats.sort(Comparator.comparingInt(Seat::getColumnNumber));
    }

    /**
     * Returns all column positions at which a contiguous block of at least
     * {@code size} consecutive AVAILABLE seats begins.
     *
     * "Contiguous" means the seat column numbers are consecutive integers
     * (no physical gap) and every seat in the span is AVAILABLE.
     * Broken, booked, and disability-reserved seats all break a run.
     *
     * Example: if cols 5-9 are all available and size=3, this returns [5, 6, 7].
     * The seating algorithm evaluates each returned position for scatter impact
     * and picks the best one.
     */
    public List<Integer> getAvailableContiguousBlocks(int size) {
        List<Integer> startPositions = new ArrayList<>();
        List<Seat> ordered = seats.stream()
                .sorted(Comparator.comparingInt(Seat::getColumnNumber))
                .collect(Collectors.toList());

        int runLength = 0;

        for (int i = 0; i < ordered.size(); i++) {
            Seat current = ordered.get(i);

            if (!current.isAvailable()) {
                runLength = 0;
                continue;
            }

            // Reset run if there is a gap in column numbering
            if (runLength > 0) {
                Seat previous = ordered.get(i - 1);
                if (current.getColumnNumber() != previous.getColumnNumber() + 1) {
                    runLength = 0;
                }
            }

            runLength++;

            // Every time runLength reaches or surpasses size, a new valid start exists
            if (runLength >= size) {
                startPositions.add(current.getColumnNumber() - size + 1);
            }
        }

        return startPositions;
    }

    /**
     * Returns the seat at the given column number, if it exists in this row.
     */
    public Optional<Seat> getSeatByColumn(int columnNumber) {
        return seats.stream()
                .filter(s -> s.getColumnNumber() == columnNumber)
                .findFirst();
    }

    /**
     * Returns the number of available seats in this row (broken/booked excluded).
     */
    public long countAvailableSeats() {
        return seats.stream().filter(Seat::isAvailable).count();
    }

    public char getRowLabel() {
        return rowLabel;
    }

    public List<Seat> getSeats() {
        return seats;
    }
}

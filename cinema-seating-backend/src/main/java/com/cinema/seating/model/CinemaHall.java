package com.cinema.seating.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Represents the entire cinema hall for a single session.
 * Responsible for building the seat grid, assigning fixed seat types
 * (VIP, disability), and randomly distributing broken seats at session start.
 *
 * Layout constants:
 *   Rows    : A–O  (15 rows; A is closest to the screen)
 *   Columns : 1–28 (all rows)
 *   Wings   : cols  1– 4  and  25–28  (regular seats, de-prioritised by algorithm)
 *   VIP     : rows E–I, cols 12–15
 *   Disability: row A, cols 1–6  (6 adjacent seats, front-left — wheelchair aisle access)
 */
public class CinemaHall {

    public static final char FIRST_ROW = 'A';
    public static final char LAST_ROW  = 'O';
    public static final int  FIRST_COL = 1;
    public static final int  LAST_COL  = 28;

    // VIP zone boundaries
    public static final char VIP_ROW_START = 'E';
    public static final char VIP_ROW_END   = 'I';
    public static final int  VIP_COL_START = 12;
    public static final int  VIP_COL_END   = 15;

    // Disability block: row A, columns 1–6 (all six seats adjacent)
    public static final char DISABILITY_ROW      = 'A';
    public static final int  DISABILITY_COL_START = 1;
    public static final int  DISABILITY_COL_END   = 6;

    // Broken seat constraints
    private static final int BROKEN_MIN         = 6;
    private static final int BROKEN_MAX         = 10;
    private static final int BROKEN_MAX_PER_ROW = 2;

    // Rows are stored in insertion order (A → O) for predictable front-to-back iteration
    private Map<Character, Row> rows;

    public CinemaHall() {
        this.rows = new LinkedHashMap<>();
    }

    /**
     * Builds the full seat grid, assigns seat types, and randomly marks broken seats.
     * Call once per session before accepting any bookings.
     */
    public void initialise() {
        rows.clear();

        // Build every row A–O with all 28 seats
        for (char rowChar = FIRST_ROW; rowChar <= LAST_ROW; rowChar++) {
            Row row = new Row(rowChar);
            for (int col = FIRST_COL; col <= LAST_COL; col++) {
                SeatType type = determineSeatType(rowChar, col);
                row.addSeat(new Seat(rowChar, col, type, SeatStatus.AVAILABLE));
            }
            rows.put(rowChar, row);
        }

        assignBrokenSeats(new Random());
    }

    /**
     * Overload that accepts a seeded Random — used in tests for deterministic layouts.
     */
    public void initialise(Random random) {
        rows.clear();
        for (char rowChar = FIRST_ROW; rowChar <= LAST_ROW; rowChar++) {
            Row row = new Row(rowChar);
            for (int col = FIRST_COL; col <= LAST_COL; col++) {
                SeatType type = determineSeatType(rowChar, col);
                row.addSeat(new Seat(rowChar, col, type, SeatStatus.AVAILABLE));
            }
            rows.put(rowChar, row);
        }
        assignBrokenSeats(random);
    }

    /**
     * Determines the structural seat type for a given position.
     * Disability is checked before VIP so the front-row block is never
     * accidentally classified as VIP even if boundaries were to overlap.
     */
    private SeatType determineSeatType(char rowChar, int col) {
        if (rowChar == DISABILITY_ROW
                && col >= DISABILITY_COL_START
                && col <= DISABILITY_COL_END) {
            return SeatType.DISABILITY;
        }
        if (rowChar >= VIP_ROW_START && rowChar <= VIP_ROW_END
                && col >= VIP_COL_START && col <= VIP_COL_END) {
            return SeatType.VIP;
        }
        return SeatType.REGULAR;
    }

    /**
     * Randomly marks between BROKEN_MIN and BROKEN_MAX seats as broken.
     *
     * Constraints enforced:
     *   1. Max BROKEN_MAX_PER_ROW broken seats per row.
     *   2. No two broken seats may be adjacent (column difference == 1) in the same row.
     *   3. Disability seats are never broken (they have fixed accessibility guarantees).
     */
    private void assignBrokenSeats(Random random) {
        int targetBroken = BROKEN_MIN + random.nextInt(BROKEN_MAX - BROKEN_MIN + 1);

        // Collect all breakable candidates (any non-disability seat)
        List<Seat> candidates = new ArrayList<>();
        for (Row row : rows.values()) {
            for (Seat seat : row.getSeats()) {
                if (seat.getSeatType() != SeatType.DISABILITY) {
                    candidates.add(seat);
                }
            }
        }
        Collections.shuffle(candidates, random);

        // Track how many broken seats each row already has, and their column positions
        Map<Character, List<Integer>> brokenColumnsByRow = new LinkedHashMap<>();

        int assigned = 0;
        for (Seat candidate : candidates) {
            if (assigned >= targetBroken) break;

            char rowChar = candidate.getRowLabel();
            int col      = candidate.getColumnNumber();

            List<Integer> rowBrokenCols = brokenColumnsByRow.computeIfAbsent(rowChar, k -> new ArrayList<>());

            if (rowBrokenCols.size() >= BROKEN_MAX_PER_ROW) continue;

            boolean adjacentToExistingBroken = rowBrokenCols.stream()
                    .anyMatch(existingCol -> Math.abs(existingCol - col) == 1);
            if (adjacentToExistingBroken) continue;

            candidate.setSeatType(SeatType.BROKEN);
            candidate.setStatus(SeatStatus.BROKEN);
            rowBrokenCols.add(col);
            assigned++;
        }
    }

    /**
     * Returns the Row for the given label, or null if the label is out of range.
     */
    public Row getRow(char rowLabel) {
        return rows.get(rowLabel);
    }

    /**
     * Convenience lookup — returns the Seat at an exact position, or null.
     */
    public Seat getSeat(char rowLabel, int columnNumber) {
        Row row = rows.get(rowLabel);
        if (row == null) return null;
        return row.getSeatByColumn(columnNumber).orElse(null);
    }

    public Map<Character, Row> getRows() {
        return rows;
    }
}

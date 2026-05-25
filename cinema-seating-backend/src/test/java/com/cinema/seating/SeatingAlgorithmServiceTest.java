package com.cinema.seating;

import com.cinema.seating.dto.BookingRequest;
import com.cinema.seating.model.CinemaHall;
import com.cinema.seating.model.Row;
import com.cinema.seating.model.Seat;
import com.cinema.seating.model.SeatStatus;
import com.cinema.seating.model.SeatType;
import com.cinema.seating.service.SeatingAlgorithmService;
import com.cinema.seating.service.SeatingAlgorithmService.PlacementCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the cinema seating algorithm.
 *
 * Tests are structured in three layers:
 *   1. CinemaHall.initialise() — structural invariants about the seat layout
 *   2. evaluateScatter()       — scatter counting logic in isolation
 *   3. findBestSeats()         — placement decision-making
 *   4. Integration             — end-to-end booking sequence
 *
 * A seeded Random(42L) is used everywhere so broken-seat placement
 * is deterministic across every test run.
 */
class SeatingAlgorithmServiceTest {

    private SeatingAlgorithmService service;
    private CinemaHall hall;

    @BeforeEach
    void setUp() {
        service = new SeatingAlgorithmService();
        // Bypass @PostConstruct and use a seeded random for a deterministic broken-seat layout
        service.getCinemaHall().initialise(new Random(42L));
        hall = service.getCinemaHall();
    }

    // =========================================================================
    // CinemaHall.initialise() — structural layout invariants
    // =========================================================================

    // Proves that the random broken-seat generator stays within the defined 6–10 limit
    @Test
    void initialise_totalBrokenSeatCount_isBetweenSixAndTen() {
        long brokenCount = hall.getRows().values().stream()
                .flatMap(row -> row.getSeats().stream())
                .filter(s -> s.getStatus() == SeatStatus.BROKEN)
                .count();

        assertTrue(brokenCount >= 6 && brokenCount <= 10,
                "Expected 6–10 broken seats but found: " + brokenCount);
    }

    // Proves that the per-row cap of 2 broken seats is respected during random placement
    @Test
    void initialise_noRowHasMoreThanTwoBrokenSeats() {
        for (char r = CinemaHall.FIRST_ROW; r <= CinemaHall.LAST_ROW; r++) {
            long rowBroken = hall.getRow(r).getSeats().stream()
                    .filter(s -> s.getStatus() == SeatStatus.BROKEN)
                    .count();

            assertTrue(rowBroken <= 2,
                    "Row " + r + " has " + rowBroken + " broken seats, exceeds max of 2");
        }
    }

    // Proves the adjacency constraint: no two broken seats in the same row are next to each other
    @Test
    void initialise_noTwoBrokenSeatsAreAdjacentInTheSameRow() {
        for (char r = CinemaHall.FIRST_ROW; r <= CinemaHall.LAST_ROW; r++) {
            List<Integer> brokenCols = hall.getRow(r).getSeats().stream()
                    .filter(s -> s.getStatus() == SeatStatus.BROKEN)
                    .map(Seat::getColumnNumber)
                    .sorted()
                    .collect(Collectors.toList());

            for (int i = 1; i < brokenCols.size(); i++) {
                int gap = brokenCols.get(i) - brokenCols.get(i - 1);
                assertTrue(gap > 1,
                        "Row " + r + ": broken seats at cols "
                        + brokenCols.get(i - 1) + " and " + brokenCols.get(i)
                        + " are adjacent (gap = " + gap + ")");
            }
        }
    }

    // Proves VIP classification is confined exactly to rows E–I, cols 12–15 — no leakage
    @Test
    void initialise_vipSeats_existOnlyWithinDefinedZone() {
        for (char r = CinemaHall.FIRST_ROW; r <= CinemaHall.LAST_ROW; r++) {
            for (Seat seat : hall.getRow(r).getSeats()) {
                boolean inVipZone = r >= CinemaHall.VIP_ROW_START
                        && r <= CinemaHall.VIP_ROW_END
                        && seat.getColumnNumber() >= CinemaHall.VIP_COL_START
                        && seat.getColumnNumber() <= CinemaHall.VIP_COL_END;

                if (inVipZone) {
                    // Seat may be VIP or BROKEN (a broken seat inside the zone is still permitted)
                    assertTrue(
                            seat.getSeatType() == SeatType.VIP
                            || seat.getSeatType() == SeatType.BROKEN,
                            "Seat " + seat.getSeatReference()
                            + " is in the VIP zone but classified as " + seat.getSeatType());
                } else {
                    // Any seat outside the zone must never carry VIP classification
                    assertNotEquals(SeatType.VIP, seat.getSeatType(),
                            "Seat " + seat.getSeatReference()
                            + " is outside the VIP zone but classified as VIP");
                }
            }
        }
    }

    // Proves disability seats total exactly 6, are confined to row A or B, and are contiguous
    @Test
    void initialise_disabilitySeats_areSixAdjacentSeatsInFrontRows() {
        List<Seat> disabilitySeats = new java.util.ArrayList<>();
        for (char r = CinemaHall.FIRST_ROW; r <= CinemaHall.LAST_ROW; r++) {
            hall.getRow(r).getSeats().stream()
                    .filter(s -> s.getSeatType() == SeatType.DISABILITY)
                    .forEach(disabilitySeats::add);
        }

        assertEquals(6, disabilitySeats.size(),
                "Expected exactly 6 disability seats, found: " + disabilitySeats.size());

        disabilitySeats.forEach(s ->
                assertTrue(s.getRowLabel() == 'A' || s.getRowLabel() == 'B',
                        "Disability seat " + s.getSeatReference()
                        + " is not in row A or B"));

        // Sort by column and verify every consecutive pair has a gap of exactly 1
        disabilitySeats.sort(Comparator.comparingInt(Seat::getColumnNumber));
        for (int i = 1; i < disabilitySeats.size(); i++) {
            int colA = disabilitySeats.get(i - 1).getColumnNumber();
            int colB = disabilitySeats.get(i).getColumnNumber();
            assertEquals(colA + 1, colB,
                    "Disability seats are not contiguous: col " + colA
                    + " and col " + colB + " are not adjacent");
        }
    }

    // =========================================================================
    // evaluateScatter() — scatter counting logic
    // =========================================================================

    // A solo placed directly after a booked block merges with it; the remaining
    // run to the right is open-ended so no seat becomes trapped
    @Test
    void evaluateScatter_soloJoiningExistingBookedBlock_producesZeroScatter() {
        // [BOOKED 1-3] [AVAILABLE 4] [AVAILABLE 5-28]
        // Placing solo at 4 → remaining run 5-28 has open right side, never isolated
        Row row = buildRow('P', col -> col <= 3);
        assertEquals(0, service.evaluateScatter(row, 4, 1));
    }

    // A solo placed at the very start of a free run that follows a booked section
    // cannot isolate anything because the rest of the run extends freely to the right
    @Test
    void evaluateScatter_soloAtLeadingEdgeOfFreeRun_producesZeroScatter() {
        // [BOOKED 1-4] [AVAILABLE 5-28]
        // Placing solo at 5 → remaining run 6-28 has open right side
        Row row = buildRow('P', col -> col <= 4);
        assertEquals(0, service.evaluateScatter(row, 5, 1));
    }

    // Filling the only remaining seat in the row leaves no available seats to become isolated
    @Test
    void evaluateScatter_soloFillsLastAvailableSeat_producesZeroScatter() {
        // [BOOKED 1-3] [AVAILABLE 4] [BOOKED 5-28]
        // Col 4 is the only available seat; placing a solo there means no seats remain to check
        Row row = buildRow('P', col -> col <= 3 || col >= 5);
        assertEquals(0, service.evaluateScatter(row, 4, 1));
    }

    // Proves the core scatter detection: placing a solo at one end of a two-seat gap
    // traps the other seat between a booked neighbour and the simulated placement,
    // creating exactly one isolated gap; a group of 2 fills the whole gap cleanly
    @Test
    void evaluateScatter_soloInTwoSeatGap_isolatesRemainingGapSeat() {
        // [BOOKED 1-3] [AVAILABLE 4-5] [BOOKED 6-28]
        Row row = buildRow('P', col -> col <= 3 || col >= 6);

        // Solo at col 4 → col 5 is left with booked col 6 on the right and simulated col 4 on the left
        assertEquals(1, service.evaluateScatter(row, 4, 1),
                "Solo at col 4 should leave col 5 isolated (scatter = 1)");

        // Solo at col 5 → col 4 is left with booked col 3 on the left and simulated col 5 on the right
        assertEquals(1, service.evaluateScatter(row, 5, 1),
                "Solo at col 5 should leave col 4 isolated (scatter = 1)");

        // Group of 2 fills the complete gap — no available seats remain in the gap
        assertEquals(0, service.evaluateScatter(row, 4, 2),
                "Group of 2 covering the entire two-seat gap should produce zero scatter");
    }

    // =========================================================================
    // findBestSeats() — placement selection
    // =========================================================================

    // When one row forces scatter (3 seats squeezed between booked blocks) and a clean
    // row exists at the back, the algorithm must pick the clean row
    @Test
    void findBestSeats_prefersZeroScatterRowOverRowThatForcesScatter() {
        // Row C becomes a "trap": only cols 6-8 are available, booked on both sides
        // Any group-of-2 placement here leaves 1 isolated seat (scatter = 1)
        Row rowC = hall.getRow('C');
        clearBrokenSeats(rowC);
        bookColRange(rowC, 1, 5);    // cols 1-5 booked
        bookColRange(rowC, 9, 28);   // cols 9-28 booked
        // Remaining available: cols 6-8 only
        // group-of-2 at 6-7 → col 8 isolated; at 7-8 → col 6 isolated; scatter=1 either way

        // Row O is cleared of broken seats to guarantee a clean large block exists
        Row rowO = hall.getRow('O');
        clearBrokenSeats(rowO);

        Optional<PlacementCandidate> result = service.findBestSeats(2, "REGULAR");

        assertTrue(result.isPresent(), "Should find a placement in a mostly empty hall");
        assertEquals(0, result.get().scatterCount,
                "Algorithm must choose the zero-scatter placement, not the trapped row");
        assertNotEquals('C', result.get().row.getRowLabel(),
                "Algorithm must not choose row C which forces scatter=1");
    }

    // The block returned for a group of 4 must contain exactly 4 physically
    // consecutive available seats — proving getAvailableContiguousBlocks and findBestSeats agree
    @Test
    void findBestSeats_groupOfFour_returnsExactlyFourConsecutiveAvailableSeats() {
        Optional<PlacementCandidate> result = service.findBestSeats(4, "REGULAR");

        assertTrue(result.isPresent(), "Should find 4 consecutive seats in a near-empty hall");

        PlacementCandidate p = result.get();

        // Every column from startColumn to startColumn+3 must exist and be available
        for (int col = p.startColumn; col < p.startColumn + 4; col++) {
            Optional<Seat> seat = p.row.getSeatByColumn(col);
            assertTrue(seat.isPresent(),     "Expected seat at col " + col + " in row " + p.row.getRowLabel());
            assertTrue(seat.get().isAvailable(), "Seat " + p.row.getRowLabel() + col + " is not available");
        }

        // The block must end at exactly startColumn+3 (no phantom extension)
        int expectedEnd = p.startColumn + 3;
        assertEquals(expectedEnd,
                p.row.getSeatByColumn(expectedEnd).map(Seat::getColumnNumber).orElse(-1),
                "Block end column should be startColumn + 3 = " + expectedEnd);
    }

    // =========================================================================
    // Integration test — 20 sequential bookings
    // =========================================================================

    // After 20 bookings of varying sizes, no row with 3+ available regular seats
    // should contain a seat isolated by BOOKED neighbours on both sides —
    // if such a seat exists it means the algorithm created an unrecoverable wasted gap
    @Test
    void twentySequentialBookings_noBookingInducedIsolatedGapsInPartiallyFilledRows() {
        int[] groupSizes = {2, 3, 4, 2, 5, 1, 3, 2, 4, 1, 3, 2, 5, 3, 4, 2, 1, 3, 2, 4};

        for (int size : groupSizes) {
            // Suppress any unchecked result; in a near-empty hall this will never throw
            service.bookSeats(new BookingRequest(size, "REGULAR", false, null));
        }

        for (char r = CinemaHall.FIRST_ROW; r <= CinemaHall.LAST_ROW; r++) {
            Row row = hall.getRow(r);

            long regularAvailable = row.getSeats().stream()
                    .filter(s -> s.isAvailable() && s.getSeatType() == SeatType.REGULAR)
                    .count();

            // Only audit rows that still have meaningful available space
            if (regularAvailable >= 3) {
                long bookingIsolated = countBookingIsolatedSeats(row);
                assertEquals(0, bookingIsolated,
                        "Row " + r + " has " + regularAvailable + " available seats "
                        + "but " + bookingIsolated
                        + " seat(s) trapped between BOOKED neighbours — algorithm created a wasted gap");
            }
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Builds a full 28-seat row (cols 1–28) where every column whose number
     * satisfies {@code isBooked} is set to BOOKED; all others are AVAILABLE.
     * Label 'P' is used (outside A–O) so this row has no hall-state side-effects.
     */
    private Row buildRow(char label, IntPredicate isBooked) {
        Row row = new Row(label);
        for (int col = CinemaHall.FIRST_COL; col <= CinemaHall.LAST_COL; col++) {
            SeatStatus status = isBooked.test(col) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
            row.addSeat(new Seat(label, col, SeatType.REGULAR, status));
        }
        return row;
    }

    /** Resets any BROKEN seats in the given row to AVAILABLE/REGULAR for controlled test setup. */
    private void clearBrokenSeats(Row row) {
        row.getSeats().stream()
                .filter(s -> s.getStatus() == SeatStatus.BROKEN)
                .forEach(s -> {
                    s.setStatus(SeatStatus.AVAILABLE);
                    s.setSeatType(SeatType.REGULAR);
                });
    }

    /** Books every seat in the inclusive column range [fromCol, toCol] in the given row. */
    private void bookColRange(Row row, int fromCol, int toCol) {
        for (int col = fromCol; col <= toCol; col++) {
            row.getSeatByColumn(col).ifPresent(s -> s.setStatus(SeatStatus.BOOKED));
        }
    }

    /**
     * Counts available seats in a row that are isolated specifically by BOOKED seats
     * on both sides. This distinguishes algorithm-caused scatter from broken-seat-caused
     * scatter, which is an initialisation concern outside the algorithm's control.
     *
     * Edge seats (col 1 and col 28) are excluded because they remain aisle-accessible.
     */
    private long countBookingIsolatedSeats(Row row) {
        return row.getSeats().stream().filter(seat -> {
            if (!seat.isAvailable())                       return false;
            if (seat.getSeatType() == SeatType.DISABILITY) return false;

            Optional<Seat> left  = row.getSeatByColumn(seat.getColumnNumber() - 1);
            Optional<Seat> right = row.getSeatByColumn(seat.getColumnNumber() + 1);

            if (!left.isPresent() || !right.isPresent()) return false; // aisle-side edge

            // Count only isolation caused by BOOKED neighbours, not BROKEN ones
            return left.get().getStatus()  == SeatStatus.BOOKED
                && right.get().getStatus() == SeatStatus.BOOKED;
        }).count();
    }
}

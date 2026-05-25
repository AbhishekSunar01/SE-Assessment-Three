package com.cinema.seating.service;

import com.cinema.seating.dto.BookingRequest;
import com.cinema.seating.model.Booking;
import com.cinema.seating.model.CinemaHall;
import com.cinema.seating.model.Row;
import com.cinema.seating.model.Seat;
import com.cinema.seating.model.SeatStatus;
import com.cinema.seating.model.SeatType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core service that owns both the cinema hall state and all seating decisions.
 *
 * Responsibilities:
 *   - Initialise the hall at application startup (seat types, broken seats)
 *   - Run the scatter-minimisation algorithm to pick the best group placement
 *   - Commit bookings and maintain booking history
 *   - Expose hall state for the REST layer to serialise to JSON
 */
@Service
public class SeatingAlgorithmService {

    /**
     * Horizontal midpoint of the 28-column row (cols 1–28).
     * Used as a tiebreaker: when two placements tie on scatter score, the one
     * whose group centre is closest to this column is preferred.
     */
    private static final int CENTER_COL = (CinemaHall.FIRST_COL + CinemaHall.LAST_COL) / 2; // = 14

    private final CinemaHall cinemaHall;
    private final List<Booking> bookingHistory;

    public SeatingAlgorithmService() {
        this.cinemaHall     = new CinemaHall();
        this.bookingHistory = new ArrayList<>();
    }

    @PostConstruct
    public void init() {
        cinemaHall.initialise();
    }

    // =========================================================================
    // ALGORITHM — evaluateScatter
    // =========================================================================

    /**
     * HOW THIS WORKS — Scatter Evaluation
     *
     * Before placing any group, we simulate what the row would look like
     * AFTER the placement and count how many "wasted" single-seat gaps would
     * remain. A gap is wasted (scattered) if no future group of any size can
     * ever fill it — which happens when every seat around it is blocked.
     *
     * Specifically, a seat is counted as scattered if ALL of the following are true:
     *
     *   1. It is currently AVAILABLE (not broken, not booked, not a disability seat).
     *   2. It is NOT part of the group we are simulating.
     *   3. Its immediate LEFT neighbour (col - 1) is "blocked" — meaning that
     *      neighbour is BOOKED, BROKEN, a reserved DISABILITY seat, or part of
     *      the simulated group itself.
     *   4. Its immediate RIGHT neighbour (col + 1) is "blocked" for the same reasons.
     *   5. BOTH neighbours physically exist as seats in the row.
     *      Seats at the very edge of the row (col 1 or col 28) are never scattered
     *      because a solo patron can reach them from the side aisle — they always
     *      have a "free approach" on one side.
     *
     * The method does NOT modify any seat; it is a pure read-only simulation.
     * The caller compares the returned count across multiple candidate positions
     * and rows to choose the placement that wastes the fewest future seats.
     *
     * @param row       the row being evaluated
     * @param startCol  the column where the first seat of the group would be placed
     * @param groupSize number of consecutive seats the group needs
     * @return          number of isolated single-seat gaps the placement would create
     */
    public int evaluateScatter(Row row, int startCol, int groupSize) {
        int endCol       = startCol + groupSize - 1;
        int scatterCount = 0;

        for (Seat seat : row.getSeats()) {
            int col = seat.getColumnNumber();

            // Skip seats that are unavailable or are inside the simulated group
            if (!seat.isAvailable() || (col >= startCol && col <= endCol)) {
                continue;
            }

            // Disability seats are governed by their own booking path;
            // do not count them as potential wasted gaps
            if (seat.getSeatType() == SeatType.DISABILITY) {
                continue;
            }

            Optional<Seat> leftNeighbour  = row.getSeatByColumn(col - 1);
            Optional<Seat> rightNeighbour = row.getSeatByColumn(col + 1);

            // If either side has no physical seat, this is an edge seat —
            // always reachable from the aisle, never isolated
            if (!leftNeighbour.isPresent() || !rightNeighbour.isPresent()) {
                continue;
            }

            boolean leftBlocked  = isSeatBlockedForScatter(leftNeighbour.get(),  startCol, endCol);
            boolean rightBlocked = isSeatBlockedForScatter(rightNeighbour.get(), startCol, endCol);

            if (leftBlocked && rightBlocked) {
                scatterCount++;
            }
        }

        return scatterCount;
    }

    /**
     * A neighbouring seat is "blocked" for scatter evaluation purposes when it
     * cannot be occupied by a future normal booking:
     *   - It is part of the group currently being simulated (will be booked)
     *   - It is already BOOKED or BROKEN
     *   - It is a DISABILITY-reserved seat (inaccessible to regular patrons)
     */
    private boolean isSeatBlockedForScatter(Seat neighbour, int simulatedStart, int simulatedEnd) {
        int col = neighbour.getColumnNumber();
        if (col >= simulatedStart && col <= simulatedEnd) return true;
        if (!neighbour.isAvailable())                     return true;
        if (neighbour.getSeatType() == SeatType.DISABILITY) return true;
        return false;
    }

    // =========================================================================
    // ALGORITHM — findBestSeats
    // =========================================================================

    /**
     * HOW THIS WORKS — Best Seat Finder
     *
     * This method answers the question: "Where is the best place to put this group
     * so that we waste as few future seats as possible?"
     *
     * Step 1 — Build the candidate row list.
     *   Rows are ordered back-to-front (O → A for regular, I → E for VIP) because
     *   cinema patrons prefer middle and rear seats. This ordering means that when
     *   two rows tie on scatter score, the algorithm automatically favours the
     *   row further from the screen — no extra logic needed.
     *
     * Step 2 — For each row, ask Row.getAvailableContiguousBlocks(groupSize)
     *   to give every column position where a run of at least groupSize free seats
     *   begins. Each position is a candidate starting column.
     *
     * Step 3 — Zone filtering.
     *   VIP requests: the group must fit entirely within cols 12–15 (the VIP block).
     *   Regular requests: the group must NOT overlap cols 12–15 in rows E–I
     *   (regular patrons must not accidentally receive VIP seats).
     *
     * Step 4 — Score each valid candidate with evaluateScatter().
     *   We track the globally best placement seen so far using three tiebreakers:
     *
     *   TIEBREAKER 1 — Scatter count (primary, most important):
     *     A placement with scatter=0 always beats one with scatter=1, regardless
     *     of row or position. Lower scatter is always preferred.
     *
     *   TIEBREAKER 2 — Row position (secondary):
     *     Because we iterate O → A, the first row that achieves a given scatter
     *     score is the furthest-back row at that score. We never overwrite the
     *     best placement for a different row with equal scatter — the back row wins.
     *
     *   TIEBREAKER 3 — Distance from screen centre (tertiary, same row only):
     *     Within a single row, if two start columns produce identical scatter,
     *     prefer the one whose group midpoint is closest to column 14.
     *
     * Step 5 — Return the winner as a PlacementCandidate, or empty if no valid
     *   placement exists (hall full, group too large for any remaining block,
     *   or VIP zone fully occupied).
     *
     * @param groupSize           number of consecutive seats required
     * @param seatTypePreference  "REGULAR", "VIP", or "DISABILITY" (case-insensitive)
     * @return                    the best placement, or empty if none found
     */
    public Optional<PlacementCandidate> findBestSeats(int groupSize, String seatTypePreference) {
        SeatType preference = parseSeatTypePreference(seatTypePreference);

        if (preference == SeatType.DISABILITY) {
            return findDisabilityPlacement(groupSize);
        }

        List<Character> candidateRowOrder = buildCandidateRowOrder(preference);
        PlacementCandidate best = null;

        for (char rowChar : candidateRowOrder) {
            Row row = cinemaHall.getRow(rowChar);
            if (row == null) continue;

            List<Integer> blockStarts = row.getAvailableContiguousBlocks(groupSize);

            for (int startCol : blockStarts) {

                if (!isPlacementInValidZone(rowChar, startCol, groupSize, preference)) {
                    continue;
                }

                int scatter = evaluateScatter(row, startCol, groupSize);

                if (best == null) {
                    best = new PlacementCandidate(row, startCol, scatter);

                } else if (scatter < best.scatterCount) {
                    // Strictly fewer isolated gaps — always prefer this placement
                    best = new PlacementCandidate(row, startCol, scatter);

                } else if (scatter == best.scatterCount
                        && row.getRowLabel() == best.row.getRowLabel()) {
                    // Same row, same scatter: prefer whichever start column
                    // puts the group closer to the horizontal centre of the screen
                    if (distanceFromCentre(startCol, groupSize)
                            < distanceFromCentre(best.startColumn, groupSize)) {
                        best = new PlacementCandidate(row, startCol, scatter);
                    }
                }
                // Different row, same scatter: keep current best (earlier-visited = further back)
            }
        }

        return Optional.ofNullable(best);
    }

    // =========================================================================
    // BOOKING OPERATIONS
    // =========================================================================

    /**
     * Primary entry point for all bookings arriving from the REST layer.
     * For admin-override requests, seating rules are bypassed entirely.
     * For normal requests, findBestSeats() picks the placement, then this method
     * commits the state change on the seat objects and records the booking.
     */
    public Booking bookSeats(BookingRequest request) {
        validateBookingRequest(request);

        if (request.isAdminOverride()) {
            return performAdminOverride(request.getOverrideSeats());
        }

        Optional<PlacementCandidate> candidate = findBestSeats(
                request.getGroupSize(), request.getSeatTypePreference());

        if (!candidate.isPresent()) {
            throw new IllegalStateException(
                    "No suitable seats available for a group of " + request.getGroupSize()
                    + " (preference: " + request.getSeatTypePreference() + ")");
        }

        return commitPlacement(
                candidate.get(),
                request.getGroupSize(),
                parseSeatTypePreference(request.getSeatTypePreference()));
    }

    /**
     * Admin override: directly marks all seats in a single row as BOOKED.
     * Bypasses every seating rule — scatter prevention, zone restrictions,
     * broken-seat protection, and group-size limits are all ignored.
     *
     * @param rowLabel      single-character row identifier, e.g. "G"
     * @param columnNumbers columns to force-book within that row
     * @return              a Booking record for the overridden seats
     */
    public Booking adminOverride(String rowLabel, List<Integer> columnNumbers) {
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("Row label must not be empty");
        }
        char rowChar = rowLabel.toUpperCase().charAt(0);
        List<Seat> forcedSeats = new ArrayList<>();

        for (int col : columnNumbers) {
            Seat seat = cinemaHall.getSeat(rowChar, col);
            if (seat == null) {
                throw new IllegalArgumentException(
                        "Seat not found — row: " + rowChar + ", col: " + col);
            }
            seat.setStatus(SeatStatus.BOOKED);
            forcedSeats.add(seat);
        }

        Booking booking = new Booking(forcedSeats.size(), SeatType.REGULAR, forcedSeats);
        bookingHistory.add(booking);
        return booking;
    }

    /**
     * Returns a snapshot of every seat in the hall, structured for JSON serialisation.
     *
     * Shape:
     * {
     *   "A": [ {"columnNumber":1, "seatType":"DISABILITY", "status":"AVAILABLE", "seatReference":"A1"}, … ],
     *   "B": [ … ],
     *   …
     * }
     */
    public Map<String, List<Map<String, Object>>> getHallState() {
        Map<String, List<Map<String, Object>>> state = new LinkedHashMap<>();

        for (Map.Entry<Character, Row> entry : cinemaHall.getRows().entrySet()) {
            String rowKey = String.valueOf(entry.getKey());
            List<Map<String, Object>> seatList = new ArrayList<>();

            for (Seat seat : entry.getValue().getSeats()) {
                Map<String, Object> seatData = new LinkedHashMap<>();
                seatData.put("columnNumber",  seat.getColumnNumber());
                seatData.put("seatType",      seat.getSeatType().name());
                seatData.put("status",        seat.getStatus().name());
                seatData.put("seatReference", seat.getSeatReference());
                seatList.add(seatData);
            }

            state.put(rowKey, seatList);
        }

        return state;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Builds the row scanning order for findBestSeats().
     * Regular: all rows O → A (back-of-cinema first, patron preference).
     * VIP:     only rows I → E (back-of-VIP-zone first).
     */
    private List<Character> buildCandidateRowOrder(SeatType preference) {
        List<Character> order = new ArrayList<>();
        if (preference == SeatType.VIP) {
            for (char r = CinemaHall.VIP_ROW_END; r >= CinemaHall.VIP_ROW_START; r--) {
                order.add(r);
            }
        } else {
            for (char r = CinemaHall.LAST_ROW; r >= CinemaHall.FIRST_ROW; r--) {
                order.add(r);
            }
        }
        return order;
    }

    /**
     * Returns true if the proposed group placement is permitted in its zone.
     *
     * VIP request   — group must sit entirely within cols 12–15.
     * Regular request in a VIP row — group must NOT overlap cols 12–15 at all.
     * Regular request in any other row — always permitted.
     */
    private boolean isPlacementInValidZone(char rowChar, int startCol,
                                           int groupSize, SeatType preference) {
        int endCol = startCol + groupSize - 1;

        if (preference == SeatType.VIP) {
            return startCol >= CinemaHall.VIP_COL_START
                && endCol   <= CinemaHall.VIP_COL_END;
        }

        // Regular booking: block any overlap with the VIP zone in VIP rows
        if (rowChar >= CinemaHall.VIP_ROW_START && rowChar <= CinemaHall.VIP_ROW_END) {
            boolean overlapsVip = startCol <= CinemaHall.VIP_COL_END
                               && endCol   >= CinemaHall.VIP_COL_START;
            return !overlapsVip;
        }

        return true;
    }

    /**
     * Handles DISABILITY preference separately: locates a contiguous block of
     * available disability seats large enough for the group.
     * The disability zone is always row A, cols 1–6.
     */
    private Optional<PlacementCandidate> findDisabilityPlacement(int groupSize) {
        Row frontRow = cinemaHall.getRow(CinemaHall.DISABILITY_ROW);
        if (frontRow == null) return Optional.empty();

        for (int startCol : frontRow.getAvailableContiguousBlocks(groupSize)) {
            int endCol = startCol + groupSize - 1;
            if (startCol >= CinemaHall.DISABILITY_COL_START
                    && endCol <= CinemaHall.DISABILITY_COL_END) {
                return Optional.of(new PlacementCandidate(
                        frontRow, startCol,
                        evaluateScatter(frontRow, startCol, groupSize)));
            }
        }
        return Optional.empty();
    }

    /**
     * Atomically marks each seat in the chosen placement as BOOKED and records
     * the resulting Booking in the history. Called only after findBestSeats()
     * has confirmed a valid candidate exists.
     */
    private Booking commitPlacement(PlacementCandidate placement,
                                    int groupSize, SeatType preference) {
        List<Seat> assignedSeats = new ArrayList<>();
        for (int col = placement.startColumn; col < placement.startColumn + groupSize; col++) {
            final int currentCol = col; // captured as effectively final for the lambda below
            Seat seat = placement.row.getSeatByColumn(currentCol)
                    .orElseThrow(() -> new IllegalStateException(
                            "Expected seat missing during booking commit at col " + currentCol));
            seat.setStatus(SeatStatus.BOOKED);
            assignedSeats.add(seat);
        }
        Booking booking = new Booking(groupSize, preference, assignedSeats);
        bookingHistory.add(booking);
        return booking;
    }

    /**
     * Handles the admin-override path inside bookSeats().
     * Parses seat references in "A14" format (row letter + column number)
     * and force-books each seat, skipping all validation.
     */
    private Booking performAdminOverride(List<String> seatReferences) {
        if (seatReferences == null || seatReferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "Admin override requires at least one seat reference");
        }
        List<Seat> seats = new ArrayList<>();
        for (String ref : seatReferences) {
            String cleaned = ref.trim().toUpperCase();
            if (cleaned.length() < 2) {
                throw new IllegalArgumentException("Invalid seat reference: " + ref);
            }
            char rowChar = cleaned.charAt(0);
            int col;
            try {
                col = Integer.parseInt(cleaned.substring(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Cannot parse column number in seat reference: " + ref);
            }
            Seat seat = cinemaHall.getSeat(rowChar, col);
            if (seat == null) {
                throw new IllegalArgumentException("Seat not found: " + ref);
            }
            seat.setStatus(SeatStatus.BOOKED);
            seats.add(seat);
        }
        Booking booking = new Booking(seats.size(), SeatType.REGULAR, seats);
        bookingHistory.add(booking);
        return booking;
    }

    /** Rejects booking requests with structurally invalid parameters at the boundary. */
    private void validateBookingRequest(BookingRequest request) {
        if (request.getGroupSize() < 1 || request.getGroupSize() > 7) {
            throw new IllegalArgumentException(
                    "Group size must be 1–7, received: " + request.getGroupSize());
        }
        if (request.isAdminOverride()
                && (request.getOverrideSeats() == null || request.getOverrideSeats().isEmpty())) {
            throw new IllegalArgumentException(
                    "Admin override flag is true but overrideSeats list is empty");
        }
    }

    /**
     * Maps a raw API string to the internal SeatType enum.
     * Null or unrecognised values default to REGULAR.
     */
    private SeatType parseSeatTypePreference(String preference) {
        if (preference == null) return SeatType.REGULAR;
        switch (preference.trim().toUpperCase()) {
            case "VIP":        return SeatType.VIP;
            case "DISABILITY": return SeatType.DISABILITY;
            default:           return SeatType.REGULAR;
        }
    }

    /**
     * Returns how far the midpoint of a proposed group is from the screen centre (col 14).
     * Lower values are preferred in the within-row tiebreaker.
     */
    private int distanceFromCentre(int startCol, int groupSize) {
        int groupMidpoint = startCol + (groupSize - 1) / 2;
        return Math.abs(groupMidpoint - CENTER_COL);
    }

    // =========================================================================
    // STATE ACCESSORS
    // =========================================================================

    public CinemaHall getCinemaHall() {
        return cinemaHall;
    }

    public List<Booking> getBookingHistory() {
        return Collections.unmodifiableList(bookingHistory);
    }

    /**
     * Reinitialises the hall and clears booking history.
     * Useful for starting a new session or resetting state in tests.
     */
    public void resetHall() {
        cinemaHall.initialise();
        bookingHistory.clear();
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Lightweight value object that captures a candidate placement found by
     * findBestSeats(). Immutable once created; consumed and discarded by
     * commitPlacement() after the booking is confirmed.
     */
    public static class PlacementCandidate {
        public final Row row;
        public final int startColumn;
        public final int scatterCount;

        public PlacementCandidate(Row row, int startColumn, int scatterCount) {
            this.row          = row;
            this.startColumn  = startColumn;
            this.scatterCount = scatterCount;
        }
    }
}

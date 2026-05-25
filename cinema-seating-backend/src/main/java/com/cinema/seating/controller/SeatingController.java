package com.cinema.seating.controller;

import com.cinema.seating.dto.AdminOverrideRequest;
import com.cinema.seating.dto.BookingRequest;
import com.cinema.seating.model.Booking;
import com.cinema.seating.model.Seat;
import com.cinema.seating.service.SeatingAlgorithmService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller that exposes the cinema seating algorithm over HTTP.
 * All endpoints are prefixed with /api and allow cross-origin requests
 * from the Next.js frontend running on localhost:3000.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class SeatingController {

    private final SeatingAlgorithmService seatingService;

    public SeatingController(SeatingAlgorithmService seatingService) {
        this.seatingService = seatingService;
    }

    // =========================================================================
    // GET /api/hall
    // =========================================================================

    /**
     * Returns the current state of every seat in the hall as a nested JSON object.
     *
     * Response shape:
     * {
     *   "A": [ {"columnNumber":1, "seatType":"DISABILITY", "status":"AVAILABLE", "seatReference":"A1"}, … ],
     *   "B": [ … ],
     *   …
     *   "O": [ … ]
     * }
     *
     * The frontend uses this to render the seat grid and colour each seat
     * according to its type and current status.
     */
    @GetMapping("/hall")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getHallState() {
        return ResponseEntity.ok(seatingService.getHallState());
    }

    // =========================================================================
    // POST /api/book
    // =========================================================================

    /**
     * Attempts to book seats for a group using the scatter-minimisation algorithm.
     *
     * Request body:
     * {
     *   "groupSize": 3,
     *   "seatTypePreference": "REGULAR" | "VIP" | "DISABILITY",
     *   "adminOverride": false,
     *   "overrideSeats": []          // only required when adminOverride is true
     * }
     *
     * Success (200):
     * {
     *   "bookingId": "...",
     *   "groupSize": 3,
     *   "seatTypePreference": "REGULAR",
     *   "assignedSeats": ["G12", "G13", "G14"]
     * }
     *
     * No seats available (409):
     * { "error": "No suitable seats available for a group of 3 (preference: REGULAR)" }
     *
     * Invalid request (400):
     * { "error": "Group size must be 1–7, received: 9" }
     */
    @PostMapping("/book")
    public ResponseEntity<Map<String, Object>> bookSeats(@RequestBody BookingRequest request) {
        try {
            Booking booking = seatingService.bookSeats(request);
            return ResponseEntity.ok(buildBookingResponse(booking));

        } catch (IllegalArgumentException e) {
            return buildError(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (IllegalStateException e) {
            // Hall is full or group cannot fit anywhere without excessive scatter
            return buildError(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    // =========================================================================
    // POST /api/admin/override
    // =========================================================================

    /**
     * Forces specific seats in a single row to BOOKED status, bypassing all
     * seating rules (scatter prevention, zone restrictions, broken-seat protection).
     * Intended for staff use only.
     *
     * Request body:
     * {
     *   "rowLabel": "G",
     *   "columnNumbers": [12, 13, 14]
     * }
     *
     * Success (200):
     * {
     *   "message": "Admin override applied",
     *   "bookingId": "...",
     *   "assignedSeats": ["G12", "G13", "G14"]
     * }
     *
     * Invalid row or column (400):
     * { "error": "Seat not found — row: Z, col: 5" }
     */
    @PostMapping("/admin/override")
    public ResponseEntity<Map<String, Object>> adminOverride(
            @RequestBody AdminOverrideRequest request) {
        try {
            Booking booking = seatingService.adminOverride(
                    request.getRowLabel(), request.getColumnNumbers());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message",      "Admin override applied");
            body.put("bookingId",    booking.getBookingId());
            body.put("assignedSeats", booking.getAssignedSeats().stream()
                    .map(Seat::getSeatReference)
                    .collect(Collectors.toList()));
            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {
            return buildError(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // =========================================================================
    // POST /api/session/reset
    // =========================================================================

    /**
     * Reinitialises the cinema hall for a new session:
     * clears all bookings and regenerates broken seats randomly.
     * The fresh hall state is returned immediately so the frontend
     * can re-render without a separate GET /api/hall call.
     *
     * Response (200):
     * {
     *   "message": "Hall reset — new session started",
     *   "hall": { … full hall state … }
     * }
     */
    @PostMapping("/session/reset")
    public ResponseEntity<Map<String, Object>> resetSession() {
        seatingService.resetHall();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Hall reset — new session started");
        body.put("hall",    seatingService.getHallState());
        return ResponseEntity.ok(body);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Converts a Booking domain object into a clean JSON-friendly map.
     * Avoids exposing nested Seat internals directly; only seat references are sent.
     */
    private Map<String, Object> buildBookingResponse(Booking booking) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bookingId",          booking.getBookingId());
        response.put("groupSize",          booking.getGroupSize());
        response.put("seatTypePreference", booking.getSeatTypePreference().name());
        response.put("assignedSeats",      booking.getAssignedSeats().stream()
                .map(Seat::getSeatReference)
                .collect(Collectors.toList()));
        return response;
    }

    /** Wraps an error message in a consistent JSON envelope. */
    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}

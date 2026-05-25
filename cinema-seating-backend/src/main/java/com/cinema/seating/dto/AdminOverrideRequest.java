package com.cinema.seating.dto;

import java.util.List;

/**
 * Request body for the POST /api/admin/override endpoint.
 * Targets a single row and a list of column numbers to force-book.
 */
public class AdminOverrideRequest {

    /** Single-character row identifier, e.g. "G". */
    private String rowLabel;

    /** Column numbers within that row to mark as BOOKED regardless of their current state. */
    private List<Integer> columnNumbers;

    public AdminOverrideRequest() {
    }

    public AdminOverrideRequest(String rowLabel, List<Integer> columnNumbers) {
        this.rowLabel      = rowLabel;
        this.columnNumbers = columnNumbers;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    public List<Integer> getColumnNumbers() {
        return columnNumbers;
    }

    public void setColumnNumbers(List<Integer> columnNumbers) {
        this.columnNumbers = columnNumbers;
    }
}

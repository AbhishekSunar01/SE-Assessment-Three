"use client";

import { useState } from "react";
import type { BookingResult } from "@/lib/types";

interface Props {
  api: string;
  onBooked: (result: BookingResult) => void;
}

export default function BookingPanel({ api, onBooked }: Props) {
  const [groupSize, setGroupSize] = useState(1);
  const [preference, setPreference] = useState("REGULAR");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<BookingResult | null>(null);

  const handleBook = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${api}/book`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ groupSize, seatTypePreference: preference }),
      });
      if (res.status === 409) {
        setError("No suitable seats available for this combination.");
        return;
      }
      if (!res.ok) {
        const text = await res.text();
        setError(text || `Server error ${res.status}`);
        return;
      }
      const result: BookingResult = await res.json();
      setLastResult(result);
      onBooked(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-gray-900 rounded-xl p-4 border border-gray-700">
      <h2 className="text-[11px] font-semibold text-gray-400 uppercase tracking-widest mb-3">
        Book Seats
      </h2>

      <div className="space-y-3">
        <div>
          <label className="block text-xs text-gray-400 mb-1">
            Group size (1–7)
          </label>
          <input
            type="number"
            min={1}
            max={7}
            value={groupSize}
            onChange={(e) =>
              setGroupSize(Math.max(1, Math.min(7, Number(e.target.value))))
            }
            className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
          />
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">
            Seat preference
          </label>
          <select
            value={preference}
            onChange={(e) => setPreference(e.target.value)}
            className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
          >
            <option value="REGULAR">Regular</option>
            <option value="VIP">VIP (rows E–I, cols 12–15)</option>
            <option value="DISABILITY">Disability (row A, cols 1–6)</option>
          </select>
        </div>

        <button
          onClick={handleBook}
          disabled={loading}
          className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 text-white text-sm font-medium py-2 rounded transition-colors cursor-pointer disabled:cursor-default"
        >
          {loading ? "Booking…" : "Book Now"}
        </button>
      </div>

      {error && (
        <p className="mt-3 text-xs text-red-400 bg-red-950/60 border border-red-800/50 px-3 py-2 rounded">
          {error}
        </p>
      )}

      {lastResult && (
        <div className="mt-3 bg-green-950/60 border border-green-700/50 rounded px-3 py-2 text-xs space-y-1">
          <p className="text-green-400 font-medium">
            Confirmed — {lastResult.groupSize} seat
            {lastResult.groupSize > 1 ? "s" : ""} (
            {lastResult.seatTypePreference})
          </p>
          <p className="text-white font-mono tracking-wide">
            {lastResult.assignedSeats.join(", ")}
          </p>
          <p className="text-gray-500 truncate" title={lastResult.bookingId}>
            ID: {lastResult.bookingId}
          </p>
        </div>
      )}
    </div>
  );
}

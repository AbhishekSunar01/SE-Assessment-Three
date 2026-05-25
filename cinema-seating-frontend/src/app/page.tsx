"use client";

import { useEffect, useState, useCallback } from "react";
import SeatGrid from "@/components/SeatGrid";
import BookingPanel from "@/components/BookingPanel";
import AdminPanel from "@/components/AdminPanel";
import type { HallState, BookingResult } from "@/lib/types";

const API = "http://localhost:8080/api";

export default function Home() {
  const [hall, setHall] = useState<HallState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastBooking, setLastBooking] = useState<BookingResult | null>(null);

  const fetchHall = useCallback(async () => {
    try {
      const res = await fetch(`${API}/hall`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: HallState = await res.json();
      setHall(data);
      setError(null);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to reach backend"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHall();
  }, [fetchHall]);

  const handleBooked = (result: BookingResult) => {
    setLastBooking(result);
    fetchHall();
  };

  const handleRefresh = () => {
    setLastBooking(null);
    fetchHall();
  };

  return (
    <main className="min-h-screen p-6">
      <div className="text-center mb-6">
        <h1 className="text-2xl font-bold text-white tracking-tight">
          Cinema Seating System
        </h1>
        <p className="text-gray-500 text-sm mt-1">
          Gap-minimising allocation algorithm &mdash; Spring Boot + Next.js
        </p>
      </div>

      {error && (
        <div className="mx-auto max-w-2xl bg-red-950/60 border border-red-800/50 text-red-300 px-4 py-3 rounded-lg mb-5 text-sm">
          ⚠ {error} &mdash; ensure the Spring Boot backend is running on port 8080.
        </div>
      )}

      {loading && (
        <p className="text-center text-gray-500 text-sm">Loading hall state…</p>
      )}

      {!loading && hall && (
        <div className="flex flex-col xl:flex-row gap-5 items-start justify-center">
          <SeatGrid hall={hall} lastBooking={lastBooking} />
          <div className="flex flex-col gap-4 w-full xl:w-72 shrink-0">
            <BookingPanel api={API} onBooked={handleBooked} />
            <AdminPanel api={API} onRefresh={handleRefresh} />
          </div>
        </div>
      )}
    </main>
  );
}

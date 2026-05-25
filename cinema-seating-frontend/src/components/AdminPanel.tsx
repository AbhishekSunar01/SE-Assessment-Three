"use client";

import { useState } from "react";

interface Props {
  api: string;
  onRefresh: () => void;
}

type Message = { type: "ok" | "err"; text: string };

export default function AdminPanel({ api, onRefresh }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [rowLabel, setRowLabel] = useState("A");
  const [columns, setColumns] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);

  const handleOverride = async () => {
    const cols = columns
      .split(",")
      .map((s) => parseInt(s.trim(), 10))
      .filter((n) => !isNaN(n) && n >= 1 && n <= 28);
    if (cols.length === 0) {
      setMessage({
        type: "err",
        text: "Enter at least one valid column (1–28).",
      });
      return;
    }
    setLoading(true);
    setMessage(null);
    try {
      const res = await fetch(`${api}/admin/override`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          rowLabel: rowLabel.toUpperCase().trim(),
          columnNumbers: cols,
        }),
      });
      if (!res.ok) {
        const text = await res.text();
        setMessage({ type: "err", text: text || `Server error ${res.status}` });
        return;
      }
      setMessage({
        type: "ok",
        text: `Force-booked row ${rowLabel.toUpperCase()} cols [${cols.join(", ")}].`,
      });
      setColumns("");
      onRefresh();
    } catch (e) {
      setMessage({
        type: "err",
        text: e instanceof Error ? e.message : "Network error",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async () => {
    if (
      !confirm(
        "Reset the entire session? All bookings will be cleared and the hall re-randomised.",
      )
    )
      return;
    setLoading(true);
    setMessage(null);
    try {
      const res = await fetch(`${api}/session/reset`, { method: "POST" });
      if (!res.ok) throw new Error(`Server error ${res.status}`);
      setMessage({ type: "ok", text: "Session reset — hall re-initialised." });
      onRefresh();
    } catch (e) {
      setMessage({
        type: "err",
        text: e instanceof Error ? e.message : "Network error",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-gray-900 rounded-xl border border-gray-700">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-3 text-[11px] font-semibold text-gray-400 hover:text-white transition-colors uppercase tracking-widest cursor-pointer"
      >
        <span>Admin Panel</span>
        <span>{expanded ? "▲" : "▼"}</span>
      </button>

      {expanded && (
        <div className="px-4 pb-4 space-y-3 border-t border-gray-700 pt-3">
          <p className="text-[11px] text-gray-500">
            Force-book specific seats, bypassing all allocation rules.
          </p>

          <div className="flex gap-2">
            <div className="w-16">
              <label className="block text-xs text-gray-400 mb-1">Row</label>
              <input
                value={rowLabel}
                onChange={(e) => setRowLabel(e.target.value)}
                maxLength={1}
                className="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white text-center uppercase focus:outline-none focus:border-yellow-500"
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-400 mb-1">
                Columns (comma-separated)
              </label>
              <input
                value={columns}
                onChange={(e) => setColumns(e.target.value)}
                placeholder="e.g. 5, 6, 7"
                className="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-yellow-500"
              />
            </div>
          </div>

          <button
            onClick={handleOverride}
            disabled={loading}
            className="w-full bg-yellow-600 hover:bg-yellow-500 disabled:bg-gray-700 text-white text-sm font-medium py-2 rounded transition-colors cursor-pointer disabled:cursor-default"
          >
            {loading ? "Applying…" : "Force Book"}
          </button>

          <div className="border-t border-gray-700 pt-3">
            <button
              onClick={handleReset}
              disabled={loading}
              className="w-full bg-red-700 hover:bg-red-600 disabled:bg-gray-700 text-white text-sm font-medium py-2 rounded transition-colors cursor-pointer disabled:cursor-default"
            >
              Reset Session
            </button>
            <p className="text-[10px] text-gray-600 mt-1 text-center">
              Clears all bookings and re-randomises broken seats
            </p>
          </div>

          {message && (
            <p
              className={`text-xs px-3 py-2 rounded ${
                message.type === "ok"
                  ? "bg-green-950/60 border border-green-700/50 text-green-300"
                  : "bg-red-950/60 border border-red-800/50 text-red-300"
              }`}
            >
              {message.text}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

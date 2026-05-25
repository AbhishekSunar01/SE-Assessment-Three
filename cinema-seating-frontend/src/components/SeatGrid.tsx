import type { HallState, SeatData, BookingResult } from "@/lib/types";

const ROW_ORDER = "ABCDEFGHIJKLMNO".split("");

function seatBg(seat: SeatData, isJustBooked: boolean): string {
  if (isJustBooked) return "bg-green-400";
  if (seat.status === "BROKEN" || seat.seatType === "BROKEN")
    return "bg-stone-700 opacity-50";
  if (seat.status === "BOOKED") return "bg-slate-500";
  if (seat.seatType === "VIP") return "bg-amber-400";
  if (seat.seatType === "DISABILITY") return "bg-blue-400";
  return "bg-gray-400";
}

interface Props {
  hall: HallState;
  lastBooking: BookingResult | null;
}

export default function SeatGrid({ hall, lastBooking }: Props) {
  const lastSeats = new Set(lastBooking?.assignedSeats ?? []);

  const allSeats = Object.values(hall).flat();
  const available = allSeats.filter((s) => s.status === "AVAILABLE").length;
  const booked = allSeats.filter((s) => s.status === "BOOKED").length;

  return (
    <div className="bg-gray-900 rounded-xl p-5 border border-gray-700 shrink-0">
      {/* Screen */}
      <div className="flex justify-center mb-5">
        <div className="bg-gray-600 text-gray-300 text-[10px] font-semibold px-20 py-1 rounded-sm tracking-[0.25em] uppercase">
          Screen
        </div>
      </div>

      {/* Grid */}
      <div className="space-y-[3px]">
        {ROW_ORDER.map((rowLabel) => {
          const seats = hall[rowLabel];
          if (!seats) return null;
          const sorted = [...seats].sort(
            (a, b) => a.columnNumber - b.columnNumber,
          );
          return (
            <div key={rowLabel} className="flex items-center gap-[3px]">
              <span className="w-4 text-[10px] text-gray-500 text-right shrink-0">
                {rowLabel}
              </span>
              <div className="flex gap-[3px] ml-1.5">
                {sorted.map((seat) => {
                  return (
                    <div
                      key={seat.columnNumber}
                      title={`${seat.seatReference} · ${seat.seatType} · ${seat.status}`}
                      className={`w-[17px] h-[14px] rounded-[2px] cursor-default transition-colors ${seatBg(seat, lastSeats.has(seat.seatReference))}`}
                    />
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Column markers */}
      <div className="flex gap-[3px] ml-[26px] mt-1">
        {Array.from({ length: 28 }, (_, i) => i + 1).map((col) => (
          <div
            key={col}
            className="w-[17px] text-center text-[8px] text-gray-600"
          >
            {col % 5 === 0 ? col : ""}
          </div>
        ))}
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-4 text-[11px] text-gray-400">
        {[
          { cls: "bg-gray-400", label: "Regular" },
          { cls: "bg-amber-400", label: "VIP" },
          { cls: "bg-blue-400", label: "Disability" },
          { cls: "bg-slate-500", label: "Booked" },
          { cls: "bg-stone-700 opacity-50", label: "Broken" },
          { cls: "bg-green-400", label: "Just booked" },
        ].map(({ cls, label }) => (
          <span key={label} className="flex items-center gap-1">
            <span className={`inline-block w-3 h-3 rounded-[2px] ${cls}`} />
            {label}
          </span>
        ))}
      </div>

      {/* Stats bar */}
      <div className="mt-3 text-[11px] text-gray-500 text-right">
        {available} available &middot; {booked} booked &middot;{" "}
        {allSeats.length} total
      </div>
    </div>
  );
}

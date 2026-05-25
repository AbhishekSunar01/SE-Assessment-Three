export type SeatData = {
  seatReference: string; // e.g. "A1", "G14"
  columnNumber: number;
  seatType: "REGULAR" | "VIP" | "DISABILITY" | "BROKEN";
  status: "AVAILABLE" | "BOOKED" | "BROKEN";
};

export type HallState = Record<string, SeatData[]>;

export type BookingResult = {
  bookingId: string;
  groupSize: number;
  seatTypePreference: string;
  assignedSeats: string[];
};

// 예약 상태 배지 스타일/아이콘 — 단일 소스.
// traveler/bookings, guide/requests, bookings/[id] 세 페이지가 공유한다.

export const STATUS_CLS: Record<string, string> = {
  REQUESTED: "badge-amber",
  ACCEPTED: "badge-emerald",
  REJECTED: "badge-red",
  CANCELLED: "badge-gray",
  COMPLETED: "badge-indigo",
};

export const STATUS_ICON: Record<string, string> = {
  REQUESTED: "⏳",
  ACCEPTED: "✅",
  REJECTED: "❌",
  CANCELLED: "🚫",
  COMPLETED: "🏁",
};

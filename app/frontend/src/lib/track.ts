// 사용자가 "어떤 서비스 세계에 있는가"를 나타내는 트랙 (mode.ts 미러).
// companion = 동행 파트너 세계(투어·가이드 흔적 없음), tour = 인증 가이드 투어 세계.
// 서버 인증과 무관하며 브라우저에만 저장되는 화면 상태값이다.

export type Track = "companion" | "tour";

const TRACK_KEY = "track";
const TOUR_LEGAL_ACK_KEY = "tourLegalAckAt";

/** 트랙 변경을 이미 마운트된 컴포넌트(Sidebar·랜딩)에 알리는 이벤트 이름. */
export const TRACK_CHANGED_EVENT = "peerup-track-changed";

export function setTrack(track: Track) {
  if (typeof window === "undefined") return;
  localStorage.setItem(TRACK_KEY, track);
  window.dispatchEvent(new Event(TRACK_CHANGED_EVENT));
}

export function getTrack(): Track | null {
  if (typeof window === "undefined") return null;
  const t = localStorage.getItem(TRACK_KEY);
  return t === "companion" || t === "tour" ? t : null;
}

export function clearTrack() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TRACK_KEY);
  window.dispatchEvent(new Event(TRACK_CHANGED_EVENT));
}

/** 투어 세계 법적 고지(관광진흥법 §38)에 동의했는가 — 1회 동의 후 기억. */
export function getTourLegalAck(): boolean {
  if (typeof window === "undefined") return false;
  return !!localStorage.getItem(TOUR_LEGAL_ACK_KEY);
}

export function ackTourLegal() {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOUR_LEGAL_ACK_KEY, new Date().toISOString());
  window.dispatchEvent(new Event(TRACK_CHANGED_EVENT));
}

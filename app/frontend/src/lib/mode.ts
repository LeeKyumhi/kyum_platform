// 사용자가 "지금 어떤 입장으로 앱을 쓰는가"를 나타내는 모드.
// 같은 계정이라도 여행자/가이드 화면을 분리하기 위해 사용한다.
// (서버 인증과는 무관하며, 브라우저에만 저장되는 화면 상태값이다.)

export type Mode = "traveler" | "guide";

const MODE_KEY = "mode";

export function setMode(mode: Mode) {
  if (typeof window !== "undefined") localStorage.setItem(MODE_KEY, mode);
}

export function getMode(): Mode | null {
  if (typeof window === "undefined") return null;
  const m = localStorage.getItem(MODE_KEY);
  return m === "guide" || m === "traveler" ? m : null;
}

export function clearMode() {
  if (typeof window !== "undefined") localStorage.removeItem(MODE_KEY);
}

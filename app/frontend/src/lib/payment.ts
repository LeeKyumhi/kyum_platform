// 결제 오케스트레이션: prepare → PortOne V2 결제창(browser-sdk, CDN) → complete
// PortOne SDK는 npm 패키지가 아니라 CDN <script> 태그로 1회만 로드한다.

import { api } from "./api";

const SDK_URL = "https://cdn.portone.io/v2/browser-sdk.js";

/** PortOne V2 브라우저 SDK를 1회만 로드한다. */
export function loadPortOneSdk(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if ((window as any).PortOne) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${SDK_URL}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("PortOne SDK 로드 실패")));
      return;
    }
    const s = document.createElement("script");
    s.src = SDK_URL;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("PortOne SDK 로드 실패"));
    document.head.appendChild(s);
  });
}

type PrepareResponse = { merchantUid: string; amount: number; currency: string };

/**
 * 결제 전체 흐름:
 * 1) 서버 prepare → merchantUid·금액
 * 2) PortOne 결제창(requestPayment)
 * 3) 서버 complete로 재검증·확정
 * 반환: 최종 'PAID' | 'FAILED'
 */
export async function payForBooking(bookingId: number): Promise<"PAID" | "FAILED"> {
  const prep = await api<PrepareResponse>("/api/payments/prepare", {
    method: "POST",
    body: { bookingId },
    auth: true,
  });

  await loadPortOneSdk();
  const PortOne = (window as any).PortOne;

  const result = await PortOne.requestPayment({
    storeId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID,
    channelKey: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY,
    paymentId: prep.merchantUid, // V2는 paymentId가 주문 식별자
    orderName: `PeerUp 예약 #${bookingId}`,
    totalAmount: prep.amount,
    currency: "CURRENCY_KRW",
    payMethod: "CARD",
  });

  if (result.code != null) {
    // 사용자 취소/실패
    return "FAILED";
  }

  // 서버 재검증·확정 (금액은 서버가 PortOne 재조회로 검증)
  await api("/api/payments/complete", {
    method: "POST",
    body: { paymentId: result.paymentId, merchantUid: prep.merchantUid },
    auth: true,
  });

  return "PAID";
}

export async function getPaymentStatus(bookingId: number): Promise<{
  status: string;
  amount: number | null;
  currency: string | null;
}> {
  return api<{ status: string; amount: number | null; currency: string | null }>(
    `/api/payments/booking/${bookingId}`,
    { method: "GET", auth: true }
  );
}

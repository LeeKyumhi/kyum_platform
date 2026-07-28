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

type PrepareResponse = {
  merchantUid: string; amount: number; currency: string;
  // 구매자 본인 정보 — PG가 결제창 호출 시 연락처를 필수로 요구한다. 서버가 본인 것만 채워준다.
  buyerName: string | null; buyerEmail: string | null; buyerPhone: string | null;
  // 구매자 고유 식별번호(우리 user id) — 스마트로 간편결제가 결제창 호출 시 필수로 요구한다.
  buyerId: string | null;
};

/**
 * 결제 전체 흐름:
 * 1) 서버 prepare → merchantUid·금액·구매자 정보
 * 2) PortOne 결제창(requestPayment)
 * 3) 서버 complete로 재검증·확정
 *
 * 반환:
 * - 'NEED_PHONE' — 연락처 미등록. 호출부가 입력을 받아 저장한 뒤 다시 부르면 된다
 *   (prepare는 기존 PENDING 결제행을 재사용하므로 다시 불러도 주문이 새로 생기지 않는다).
 * - 'PAID' | 'FAILED'
 */
export async function payForBooking(bookingId: number): Promise<"PAID" | "FAILED" | "NEED_PHONE"> {
  const prep = await api<PrepareResponse>("/api/payments/prepare", {
    method: "POST",
    body: { bookingId },
    auth: true,
  });

  // 스마트로 등 일부 PG는 구매자 연락처가 없으면 결제창 자체를 열어주지 않는다.
  if (!prep.buyerPhone) return "NEED_PHONE";

  await loadPortOneSdk();
  const PortOne = (window as any).PortOne;

  // 결제수단은 채널에 맞춰야 한다 — 간편결제 채널은 "EASY_PAY", 카드 PG(스마트로 등) 채널은 "CARD".
  // 채널을 바꿀 때 코드를 고치지 않도록 env로 뺀다.
  const payMethod = process.env.NEXT_PUBLIC_PORTONE_PAY_METHOD || "CARD";

  // 간편결제는 "어떤 간편결제사인지"까지 요구한다(스마트로: "간편 결제 수단은 필수 입력입니다").
  // 단 이 옵션은 EASY_PAY일 때만 보내야 한다 — 카드 결제에 섞으면 PortOne이
  // "카드 결제 시 card 옵션만 허용됩니다"로 거부한다. 그래서 env 유무가 아니라 payMethod에 묶는다.
  const easyPayProvider = process.env.NEXT_PUBLIC_PORTONE_EASY_PAY_PROVIDER;
  const easyPayOption = payMethod === "EASY_PAY" && easyPayProvider
    ? { easyPay: { easyPayProvider } }
    : {};

  const result = await PortOne.requestPayment({
    storeId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID,
    channelKey: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY,
    paymentId: prep.merchantUid, // V2는 paymentId가 주문 식별자
    orderName: `PeerUp 예약 #${bookingId}`,
    totalAmount: prep.amount,
    currency: "CURRENCY_KRW",
    payMethod,
    ...easyPayOption,
    customer: {
      // 스마트로 공식문서 확인: phoneNumber는 전 결제수단 공통 필수,
      // customerId는 간편결제 필수(20자 이하). paymentId는 특수문자 불가·40자 이하.
      customerId: prep.buyerId ?? undefined,
      fullName: prep.buyerName ?? undefined,
      email: prep.buyerEmail ?? undefined,
      phoneNumber: prep.buyerPhone,
    },
  });

  if (result.code != null) {
    // 사용자 취소와 PG 거부가 같은 분기로 들어온다 — 호출부는 둘을 구분하지 않고 "실패"로 안내한다.
    // PG 거부 사유(카드사 미지원 등)는 여기서만 볼 수 있으므로 콘솔에 남긴다.
    console.error("requestPayment 실패", result.code, result.message);
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

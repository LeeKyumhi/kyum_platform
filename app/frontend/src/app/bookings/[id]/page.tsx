"use client";

// 예약 상세 (T2) — 투어 당일 카드. 참여자(여행자/가이드) 전용.
// 상태·D-day·시간(KST+내 로컬)·만남 장소 지도·상대·채팅·한국어 문구를 한 화면에.

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { payForBooking, getPaymentStatus } from "@/lib/payment";
import { useLanguage } from "@/context/LanguageContext";
import { translations as i18nDict, localeOf, QUICK_PHRASE_KEYS } from "@/lib/i18n";
import { STATUS_CLS } from "@/lib/bookingStatus";
import { kakaoMapUrl } from "@/components/TimetableBuilder";
import TripMap from "@/components/TripMap";
import { PinIcon, CalendarIcon } from "@/components/icons";
import RequestDetailsBlock from "@/components/RequestDetailsBlock";

type MeetingPlace = { name: string; address: string | null; lat: number; lng: number; url: string | null };
type Booking = {
  id: number; guideProfileId: number; guideName: string; guideHeadline: string;
  guideAvatarUrl: string | null;
  travelerId: number; travelerName: string; startAt: string; hours: number;
  totalPrice: number; currency: string; status: string; message: string | null;
  requestDetails?: string | null;
  meetingPlace: MeetingPlace | null;
};
type Me = { id: number };

function dayStart(d: Date) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); }

export default function BookingDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;
  const { t, lang } = useLanguage();
  const bd = t.bookingDetail;
  const locale = localeOf(lang);

  const [booking, setBooking] = useState<Booking | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [reported, setReported] = useState(false);
  const [payStatus, setPayStatus] = useState<string>("NONE");
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Booking>(`/api/bookings/${id}`, { auth: true })
      .then(setBooking)
      .catch(() => setError(bd.notFound));
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => {});
  }, [id, router, bd.notFound]);

  useEffect(() => {
    if (!booking?.id) return;
    getPaymentStatus(booking.id).then((r) => setPayStatus(r.status)).catch(() => {});
  }, [booking?.id]);

  async function act(path: string, confirmMsg?: string) {
    if (confirmMsg && !confirm(confirmMsg)) return;
    try {
      // PATCH가 갱신된 예약을 그대로 반환하므로 별도 재조회 불필요 (원격 DB 왕복 1회 절약)
      const fresh = await api<Booking>(`/api/bookings/${id}/${path}`, { method: "PATCH", auth: true });
      setBooking(fresh);
    } catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }

  async function handlePay() {
    if (!booking) return;
    setPaying(true);
    try {
      const result = await payForBooking(booking.id);
      if (result === "PAID") setPayStatus("PAID");
      else alert(t.payment.failed);
    } catch {
      alert(t.payment.error);
    } finally {
      setPaying(false);
    }
  }

  async function copyAddress(addr: string) {
    try { await navigator.clipboard.writeText(addr); setCopied(true); setTimeout(() => setCopied(false), 1800); } catch {}
  }

  // 계정·자격 대여 탐지: 프로필과 다른 사람이 나타났을 때 신고 (오프라인 갭을 여행자가 감시).
  async function reportImpersonation() {
    if (!confirm(bd.reportImpersonationConfirm)) return;
    try {
      await api("/api/reports", {
        method: "POST", auth: true,
        body: { targetType: "BOOKING", targetId: booking!.id, reason: "IMPERSONATION" },
      });
      setReported(true);
    } catch (err) { alert(err instanceof Error ? err.message : t.common.error); }
  }

  if (error) return (
    <main className="page px-4"><div className="container-sm">
      <Link href="/traveler/bookings" className="btn-ghost text-sm">{bd.back}</Link>
      <p className="mt-6 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>
    </div></main>
  );
  if (!booking) return (
    <main className="page flex items-center justify-center"><div className="text-sm text-stone-400">…</div></main>
  );

  const start = new Date(booking.startAt);
  const ddayN = Math.round((dayStart(start) - dayStart(new Date())) / 86400000);
  const ddayLabel = ddayN > 0 ? bd.dday.replace("{n}", String(ddayN)) : ddayN === 0 ? bd.ddayToday : bd.ddayPast;
  const viewerIsTraveler = me ? me.id === booking.travelerId : true;
  const mp = booking.meetingPlace;
  const kstStr = start.toLocaleString(locale, { timeZone: "Asia/Seoul", month: "short", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit" });
  const localStr = start.toLocaleString(locale, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  const localTz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const statusLabel = t.status[booking.status as keyof typeof t.status] ?? booking.status;
  const active = booking.status === "REQUESTED" || booking.status === "ACCEPTED";

  return (
    <main className="page px-4">
      <div className="animate-fade-up container-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <Link href={viewerIsTraveler ? "/traveler/bookings" : "/guide/requests"} className="btn-ghost text-sm">{bd.back}</Link>
          <span className={`${STATUS_CLS[booking.status] ?? "badge-gray"} py-1`}>{statusLabel}</span>
        </div>

        {/* 상대 + D-day 헤더 */}
        <div className="card mb-4 p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              {/* 여행자 화면엔 가이드 사진 — 당일 나타난 사람이 프로필과 같은지 대조용(계정 대여 탐지) */}
              {viewerIsTraveler && (
                booking.guideAvatarUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={booking.guideAvatarUrl} alt={booking.guideName}
                    className="h-14 w-14 flex-shrink-0 rounded-2xl object-cover shadow-sm ring-2 ring-white" />
                ) : (
                  <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-xl font-bold text-white shadow-sm ring-2 ring-white">
                    {booking.guideName.slice(0, 1)}
                  </div>
                )
              )}
              <div className="min-w-0">
                <p className="text-xs font-semibold text-stone-400">{viewerIsTraveler ? bd.withGuide : bd.withTraveler}</p>
                <h1 className="truncate text-xl font-extrabold text-stone-900">
                  {viewerIsTraveler ? booking.guideName : booking.travelerName}
                </h1>
                {viewerIsTraveler && booking.guideHeadline && (
                  <p className="mt-0.5 truncate text-sm text-stone-500">{booking.guideHeadline}</p>
                )}
              </div>
            </div>
            <div className="flex-shrink-0 rounded-2xl bg-gradient-to-br from-sky-500 to-cyan-500 px-3 py-2 text-center text-white shadow-sm">
              <span className="block text-lg font-extrabold leading-none">{ddayLabel}</span>
            </div>
          </div>
          {viewerIsTraveler && (
            <Link href={`/guides/${booking.guideProfileId}`} className="mt-3 inline-block text-sm font-semibold text-sky-600 hover:underline">
              {bd.viewGuide} →
            </Link>
          )}
          {/* 프로필과 다른 사람이 나왔을 때 신고 (여행자, 진행 중 예약만) */}
          {viewerIsTraveler && active && (
            reported ? (
              <p className="mt-3 rounded-xl bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700">✓ {bd.reportImpersonationDone}</p>
            ) : (
              <button onClick={reportImpersonation}
                className="mt-3 text-xs font-medium text-stone-400 underline underline-offset-2 hover:text-red-500">
                {bd.reportImpersonation}
              </button>
            )
          )}
        </div>

        {/* 일정 (KST + 내 시간) */}
        <div className="card mb-4 p-5">
          <h2 className="mb-3 flex items-center gap-2 font-bold text-stone-900">
            <CalendarIcon className="h-5 w-5 text-sky-500" /> {bd.whenTitle}
          </h2>
          <div className="flex flex-col gap-2 text-sm">
            <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5">
              <span className="text-stone-500">🇰🇷 {bd.kstLabel}</span>
              <span className="font-bold text-stone-900">{kstStr}</span>
            </div>
            {localTz !== "Asia/Seoul" && (
              <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5">
                <span className="text-stone-500">🌐 {bd.localLabel}</span>
                <span className="font-semibold text-stone-700">{localStr}</span>
              </div>
            )}
            <div className="flex items-center justify-between px-1 pt-1 text-stone-600">
              <span>⏱ {booking.hours} {bd.hours}</span>
              <span className="font-bold text-stone-900">{booking.totalPrice.toLocaleString()} {booking.currency}</span>
            </div>
          </div>
        </div>

        {booking.requestDetails && (
          <div className="card mb-4 p-5">
            <RequestDetailsBlock raw={booking.requestDetails} />
          </div>
        )}

        {/* 만남 장소 */}
        <div className="card mb-4 p-5">
          <h2 className="mb-3 flex items-center gap-2 font-bold text-stone-900">
            <PinIcon className="h-5 w-5 text-sky-500" /> {bd.meetingTitle}
          </h2>
          {mp ? (
            <div className="flex flex-col gap-3">
              <TripMap points={[{ key: "mp", name: mp.name, latitude: mp.lat, longitude: mp.lng }]} className="rounded-2xl" />
              <p className="text-sm font-bold text-stone-900">{mp.name}</p>
              {mp.address && (
                <div className="flex items-start gap-2">
                  <p className="min-w-0 flex-1 text-sm text-stone-600">{mp.address}</p>
                  <button onClick={() => copyAddress(mp.address!)}
                    className="flex-shrink-0 whitespace-nowrap rounded-full border border-stone-200 px-3 py-1 text-xs font-medium text-stone-500 hover:border-sky-300 hover:text-sky-600">
                    {copied ? `✓ ${bd.addressCopied}` : bd.copyAddress}
                  </button>
                </div>
              )}
              <a href={mp.url || kakaoMapUrl({ placeId: null, placeName: mp.name, latitude: mp.lat, longitude: mp.lng })}
                target="_blank" rel="noreferrer" className="btn-ghost w-full text-center text-sm">
                {bd.openKakao}
              </a>
            </div>
          ) : (
            <div className="rounded-xl bg-stone-50 px-4 py-5 text-center">
              <p className="mb-3 text-sm text-stone-400">{bd.noMeeting}</p>
              <Link href={`/chat/${booking.id}`} className="btn-secondary text-sm">{bd.setInChat}</Link>
            </div>
          )}
        </div>

        {/* 액션 */}
        <div className="mb-4 flex flex-wrap gap-2">
          <Link href={`/chat/${booking.id}`} className="btn-primary px-5 text-sm">💬 {bd.openChat}</Link>
          {viewerIsTraveler && booking.status === "ACCEPTED" && payStatus !== "PAID" && (
            <button onClick={handlePay} disabled={paying}
              className="btn-primary px-5 text-sm disabled:opacity-60">
              {paying ? t.payment.processing : t.payment.pay}
            </button>
          )}
          {payStatus === "PAID" && (
            <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-medium text-emerald-700">✓ {t.payment.paid}</span>
          )}
          {booking.status === "ACCEPTED" && (
            <button onClick={() => act("complete", t.travelerBookings.confirmComplete)}
              className="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-5 py-2 text-sm font-medium text-emerald-700 hover:bg-emerald-100">{bd.complete}</button>
          )}
          {viewerIsTraveler && active && (
            <button onClick={() => act("cancel", t.travelerBookings.confirmCancel)} className="btn-danger px-5 text-sm">{bd.cancel}</button>
          )}
          {viewerIsTraveler && booking.status === "COMPLETED" && (
            <Link href={`/review/${booking.id}`}
              className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-5 py-2 text-sm font-medium text-amber-700 hover:bg-amber-100">{bd.review}</Link>
          )}
        </div>

        {/* 한국어 한마디 (읽기 전용 참고) */}
        <div className="card p-5">
          <h2 className="mb-3 font-bold text-stone-900">🇰🇷 {bd.phrasesTitle}</h2>
          <div className="flex flex-col gap-1.5">
            {QUICK_PHRASE_KEYS.map((key) => (
              <div key={key} className="flex items-baseline gap-2 rounded-xl bg-stone-50 px-3 py-2">
                <span className="text-sm font-semibold text-stone-800">{i18nDict.ko.chat.quickPhrases[key]}</span>
                {lang !== "ko" && <span className="text-xs text-stone-400">{t.chat.quickPhrases[key]}</span>}
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}

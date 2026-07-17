"use client";

import { useEffect, useMemo, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import { translations as i18nDict, localeOf, QUICK_PHRASE_KEYS } from "@/lib/i18n";
import { ChevronLeftIcon } from "@/components/icons";
import { parseCard, encodePlaceCard, type PlacePayload, type PlanPayload } from "@/lib/placeCard";
import PlaceDetailModal, { type ModalPlace } from "@/components/PlaceDetailModal";
import PlacePickerModal from "@/components/PlacePickerModal";
import SharePickerModal from "@/components/SharePickerModal";
import { PlanMessageCard, PlanPreviewModal } from "@/components/PlanCard";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type Message = { id: number; senderId: number; senderName: string; content: string; createdAt: string };
type Me = { id: number; fullName: string };

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export type ChatRoomProps = {
  /** GET 대화 내역 경로 (예: /api/bookings/3/messages) */
  historyPath: string;
  /** 메시지별 번역 경로 — lang 쿼리는 컴포넌트가 붙인다 */
  translatePath: (messageId: number) => string;
  /** STOMP publish 대상 (예: /app/bookings/3/send) */
  sendDestination: string;
  /** STOMP 구독 토픽 (예: /topic/bookings/3) */
  topic: string;
  headerTitle: string;
  headerSubtitle?: string;
  /** 헤더 우측 커스텀 액션 (예: DM에서 "예약 요청하기" 버튼) */
  headerAction?: React.ReactNode;
  /** 예약 채팅에서만 전달 — 장소 카드에 "만남 장소로 지정" 버튼 노출 (Booking.meeting_place PATCH) */
  onSetMeetingPlace?: (place: PlacePayload) => Promise<void>;
};

/**
 * 실시간 1:1 채팅 화면 — 예약 채팅(/chat/[bookingId])과
 * 예약 전 문의(/messages/[id])가 경로만 바꿔 공유한다.
 */
export default function ChatRoom({
  historyPath, translatePath, sendDestination, topic, headerTitle, headerSubtitle, headerAction, onSetMeetingPlace,
}: ChatRoomProps) {
  const router = useRouter();
  const { t, lang } = useLanguage();
  const l = t.chat;

  const [me, setMe]             = useState<Me | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput]       = useState("");
  const [error, setError]       = useState("");
  const [connected, setConnected] = useState(false);

  // 번역: 메시지 id → 번역문 / 표시 여부. 자동 번역 켜면 상대 메시지를 도착 즉시 번역.
  const [translations, setTranslations] = useState<Record<number, string>>({});
  const [shownTranslations, setShownTranslations] = useState<Record<number, boolean>>({});
  const [translatingIds, setTranslatingIds] = useState<Record<number, boolean>>({});
  const [autoTranslate, setAutoTranslate] = useState(false);
  const requestedRef = useRef<Set<number>>(new Set());

  // 장소 카드 (T1/T3) + 계획 공유
  const [detailPlace, setDetailPlace] = useState<ModalPlace | null>(null);
  const [detailPlan, setDetailPlan] = useState<PlanPayload | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [meetingSetIds, setMeetingSetIds] = useState<number[]>([]);
  const [settingId, setSettingId] = useState<number | null>(null);

  const clientRef = useRef<Client | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef  = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const token = getToken();
    if (!token) { router.replace("/login"); return; }

    api<Message[]>(historyPath, { auth: true })
      .then(setMessages).catch((err) => setError(err.message));
    api<Me>("/api/users/me", { auth: true }).then(setMe);

    let active = true;
    (async () => {
      if (typeof window !== "undefined" && !(window as unknown as { global?: unknown }).global) {
        (window as unknown as { global: unknown }).global = window;
      }
      const SockJS = (await import("sockjs-client")).default;
      const client = new Client({
        webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 3000,
        onConnect: () => {
          setConnected(true);
          client.subscribe(topic, (frame) => {
            const body = JSON.parse(frame.body) as Message;
            setMessages((prev) => prev.some((m) => m.id === body.id) ? prev : [...prev, body]);
          });
        },
        onStompError: (frame) => setError("Error: " + (frame.headers["message"] ?? "")),
        onWebSocketClose: () => setConnected(false),
      });
      if (active) { clientRef.current = client; client.activate(); }
    })();

    return () => { active = false; clientRef.current?.deactivate(); };
  }, [historyPath, topic, router]);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages]);

  async function translateMessage(messageId: number) {
    if (requestedRef.current.has(messageId)) {
      // 이미 번역돼 있으면 표시만 토글
      setShownTranslations((prev) => ({ ...prev, [messageId]: !prev[messageId] }));
      return;
    }
    requestedRef.current.add(messageId);
    setTranslatingIds((prev) => ({ ...prev, [messageId]: true }));
    try {
      const res = await api<{ translated: string }>(
        `${translatePath(messageId)}?lang=${lang}`, { auth: true });
      setTranslations((prev) => ({ ...prev, [messageId]: res.translated }));
      setShownTranslations((prev) => ({ ...prev, [messageId]: true }));
    } catch {
      requestedRef.current.delete(messageId); // 실패 시 재시도 가능하게
      setTranslations((prev) => ({ ...prev, [messageId]: "" }));
    } finally {
      setTranslatingIds((prev) => ({ ...prev, [messageId]: false }));
    }
  }

  // 자동 번역: 켜져 있으면 상대방의 새 메시지를 도착하는 대로 번역
  const myId = me?.id ?? null;
  useEffect(() => {
    if (!autoTranslate || myId == null) return;
    messages
      .filter((m) => m.senderId !== myId && !requestedRef.current.has(m.id) && !parseCard(m.content))
      .forEach((m) => { translateMessage(m.id); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoTranslate, messages, myId]);

  function sendContent(content: string) {
    if (!content || !clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: sendDestination,
      body: JSON.stringify({ content }),
    });
  }

  // ── 장소 카드 전송 (T1: 장소 검색 / T3: 내 위치) ──
  function sendPlaceCard(p: PlacePayload) { sendContent(encodePlaceCard(p)); }
  function sendMyLocation() {
    setError("");
    if (typeof navigator === "undefined" || !navigator.geolocation) { setError(t.placeCard.locationError); return; }
    navigator.geolocation.getCurrentPosition(
      (pos) => sendPlaceCard({ name: t.placeCard.myLocation, lat: pos.coords.latitude, lng: pos.coords.longitude, kind: "me" }),
      () => setError(t.placeCard.locationError),
      { enableHighAccuracy: true, timeout: 8000 },
    );
  }
  async function setMeeting(messageId: number, p: PlacePayload) {
    if (!onSetMeetingPlace) return;
    setSettingId(messageId); setError("");
    try { await onSetMeetingPlace(p); setMeetingSetIds((ids) => ids.includes(messageId) ? ids : [...ids, messageId]); }
    catch (e) { setError(e instanceof Error ? e.message : t.common.error); }
    finally { setSettingId(null); }
  }
  function openPlaceDetail(place: PlacePayload, id: number) {
    setDetailPlace({
      id: String(id), name: place.name, category: null, address: place.address ?? null,
      latitude: place.lat, longitude: place.lng, placeUrl: place.url ?? null,
    });
  }

  function onSend(e: React.FormEvent) {
    e.preventDefault();
    const content = input.trim();
    if (!content) return;
    sendContent(content);
    setInput(""); inputRef.current?.focus();
  }

  // 고정 한국어 문구 — 번역 API 없이 즉시 전송. ko UI는 한국어만, en/zh UI는 "한국어 / 현지어" 형식.
  function sendQuickPhrase(key: (typeof QUICK_PHRASE_KEYS)[number]) {
    const korean = i18nDict.ko.chat.quickPhrases[key];
    const current = t.chat.quickPhrases[key];
    const content = lang === "ko" ? korean : `${korean} / ${current}`;
    sendContent(content);
  }

  // 키 입력마다 재계산하지 않도록 메시지 목록이 바뀔 때만 그룹핑
  const grouped = useMemo(() =>
    messages.reduce<Array<Message & { isFirst: boolean; isLast: boolean }>>((acc, msg, i) => {
      const prev = messages[i - 1], next = messages[i + 1];
      acc.push({ ...msg, isFirst: !prev || prev.senderId !== msg.senderId, isLast: !next || next.senderId !== msg.senderId });
      return acc;
    }, []), [messages]);

  return (
    <main className="flex h-screen flex-col">
      {/* Header */}
      <header className="flex flex-shrink-0 items-center gap-3 border-b border-stone-100 bg-white/95 px-4 pb-3 pt-16 shadow-sm backdrop-blur-md md:pt-3">
        <button
          onClick={() => router.back()}
          aria-label={l.back}
          className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full border border-stone-200 bg-white text-stone-500 shadow-sm transition-colors hover:text-stone-900"
        >
          <ChevronLeftIcon className="h-4 w-4" />
        </button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-sm font-bold text-stone-900">{headerTitle}</h1>
          {headerSubtitle && <p className="text-xs text-stone-400">{headerSubtitle}</p>}
        </div>
        {headerAction}
        <button
          onClick={() => setAutoTranslate((v) => !v)}
          className={`flex flex-shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-[11px] font-semibold transition-colors ${
            autoTranslate
              ? "border-sky-300 bg-sky-50 text-sky-600"
              : "border-stone-200 text-stone-400 hover:text-stone-600"
          }`}
        >
          🌐 {l.autoTranslate}
        </button>
        <div className="flex flex-shrink-0 items-center gap-1.5 text-xs">
          <span className={`h-2 w-2 rounded-full ${connected ? "bg-emerald-400" : "bg-stone-300 animate-pulse-dot"}`} />
          <span className={connected ? "font-medium text-emerald-600" : "text-stone-400"}>
            {connected ? l.connected : l.connecting}
          </span>
        </div>
      </header>

      {error && (
        <div className="mx-4 mt-2 flex-shrink-0 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</div>
      )}

      {/* Messages */}
      <div className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-4 py-4">
        {messages.length === 0 && !error && (
          <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
            <div className="mb-2 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-cyan-400 text-2xl shadow-md">
              💬
            </div>
            <p className="text-sm font-semibold text-stone-600">{l.empty}</p>
            <p className="text-xs text-stone-400">{l.emptySub}</p>
          </div>
        )}
        {grouped.map((m) => {
          const mine = m.senderId === myId;
          const card = parseCard(m.content);
          return (
            <div key={m.id} className={`flex items-end gap-2 ${mine ? "flex-row-reverse" : "flex-row"} ${m.isFirst ? "mt-3" : "mt-0.5"}`}>
              {!mine && (
                <div className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-400 text-xs font-bold text-white ${m.isLast ? "visible" : "invisible"}`}>
                  {m.senderName.slice(0, 1)}
                </div>
              )}
              <div className={`flex flex-col ${mine ? "items-end" : "items-start"} max-w-[80%]`}>
                {!mine && m.isFirst && <span className="mb-1 ml-1 text-xs text-stone-500">{m.senderName}</span>}
                {card?.kind === "place" ? (
                  <PlaceMessageCard
                    place={card.place} mine={mine} labels={t.placeCard}
                    onOpen={() => openPlaceDetail(card.place, m.id)}
                    canSetMeeting={!!onSetMeetingPlace}
                    done={meetingSetIds.includes(m.id)} busy={settingId === m.id}
                    onSetMeeting={() => setMeeting(m.id, card.place)}
                  />
                ) : card?.kind === "plan" ? (
                  <PlanMessageCard plan={card.plan} mine={mine} labels={t.share}
                    onOpen={() => setDetailPlan(card.plan)} />
                ) : (
                  <>
                    <div className={`px-3.5 py-2.5 text-sm leading-relaxed ${
                      mine
                        ? `bg-gradient-to-br from-sky-500 to-cyan-500 text-white ${m.isFirst ? "rounded-t-2xl" : "rounded-t-lg"} rounded-bl-2xl ${m.isLast ? "rounded-br-sm" : "rounded-br-lg"}`
                        : `border border-stone-100 bg-white text-stone-800 shadow-sm ${m.isFirst ? "rounded-t-2xl" : "rounded-t-lg"} rounded-br-2xl ${m.isLast ? "rounded-bl-sm" : "rounded-bl-lg"}`
                    }`}>
                      {m.content}
                      {!mine && shownTranslations[m.id] && translations[m.id] && (
                        <p className="mt-1.5 border-t border-stone-100 pt-1.5 text-[13px] leading-relaxed text-sky-600">
                          🌐 {translations[m.id]}
                        </p>
                      )}
                    </div>
                    {/* 상대 메시지: 번역 토글 */}
                    {!mine && (
                      <button
                        onClick={() => translateMessage(m.id)}
                        className="mx-1 mt-0.5 text-[10px] font-medium text-stone-400 transition-colors hover:text-sky-500"
                      >
                        {translatingIds[m.id]
                          ? l.translating
                          : shownTranslations[m.id] && translations[m.id]
                            ? l.hideTranslation
                            : l.translateBtn}
                      </button>
                    )}
                  </>
                )}
                {m.isLast && <span className="mx-1 mt-1 text-[10px] text-stone-400">{formatTime(m.createdAt)}</span>}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* 한국어 한마디 — 탭하면 한국어+현지어(현재 UI 언어)를 바로 전송 */}
      <div className="flex flex-shrink-0 flex-col gap-1.5 border-t border-stone-100 bg-white px-4 pt-2.5 pb-1">
        <span className="text-[10px] font-semibold text-stone-400">🇰🇷 {l.quickPhrasesLabel}</span>
        <div className="flex gap-2 overflow-x-auto pb-0.5">
          {QUICK_PHRASE_KEYS.map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => sendQuickPhrase(key)}
              disabled={!connected}
              className="flex-shrink-0 whitespace-nowrap rounded-full border border-sky-200 bg-sky-50 px-3 py-1.5 text-xs font-medium text-sky-700 transition-colors hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {l.quickPhrases[key]}
            </button>
          ))}
        </div>
      </div>

      {/* 첨부 액션 시트 — + 버튼으로 열고, 항목 선택 시 닫힘 (내 위치 / 장소 / 일정·코스 공유) */}
      {sheetOpen && (
        <div className="flex flex-shrink-0 flex-col gap-1 border-t border-stone-100 bg-white px-3 py-2">
          {[
            { icon: "📍", label: t.placeCard.sendLocation, run: sendMyLocation },
            { icon: "🗺️", label: t.placeCard.searchPlace, run: () => setPickerOpen(true) },
            { icon: "📋", label: t.share.shareAction, run: () => setShareOpen(true) },
          ].map((a) => (
            <button key={a.label} type="button" disabled={!connected}
              onClick={() => { setSheetOpen(false); a.run(); }}
              className="flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm font-medium text-stone-700 transition-colors hover:bg-sky-50 disabled:cursor-not-allowed disabled:opacity-40">
              <span className="text-lg">{a.icon}</span> {a.label}
            </button>
          ))}
        </div>
      )}

      {/* Input bar */}
      <form onSubmit={onSend} className="flex flex-shrink-0 items-center gap-2 border-t border-stone-100 bg-white px-4 pt-3 pb-20 md:pb-3">
        <button type="button" onClick={() => setSheetOpen((v) => !v)} disabled={!connected}
          aria-label={t.share.attach}
          className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full border text-2xl leading-none transition-all disabled:cursor-not-allowed disabled:opacity-40 ${
            sheetOpen ? "rotate-45 border-sky-300 bg-sky-50 text-sky-600" : "border-stone-200 bg-white text-stone-500 hover:bg-stone-50"
          }`}>+</button>
        <input ref={inputRef} value={input} onChange={(e) => setInput(e.target.value)}
          placeholder={l.placeholder}
          className="flex-1 rounded-full border border-stone-200 bg-stone-50 px-4 py-2.5 text-sm text-stone-900 placeholder-stone-400 outline-none transition-all focus:border-sky-300 focus:bg-white focus:ring-2 focus:ring-sky-100" />
        <button type="submit" disabled={!input.trim() || !connected}
          className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-sky-500 text-white shadow-sm transition-all hover:bg-sky-600 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Send">
          <svg className="h-4 w-4 rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
          </svg>
        </button>
      </form>

      {pickerOpen && (
        <PlacePickerModal onSelect={(p) => sendPlaceCard(p)} onClose={() => setPickerOpen(false)} />
      )}
      {shareOpen && (
        <SharePickerModal onShare={(content) => sendContent(content)} onClose={() => setShareOpen(false)} />
      )}
      {detailPlace && (
        <PlaceDetailModal place={detailPlace} onClose={() => setDetailPlace(null)} />
      )}
      {detailPlan && (
        <PlanPreviewModal plan={detailPlan} locale={localeOf(lang)}
          labels={t.share} onClose={() => setDetailPlan(null)} />
      )}
    </main>
  );
}

// ── 채팅 장소 카드 (텍스트 버블 밖에서 자체 스타일로 렌더 — 흰 배경 흰 글자 방지) ──
function PlaceMessageCard({ place, mine, labels, onOpen, canSetMeeting, done, busy, onSetMeeting }: {
  place: PlacePayload; mine: boolean; labels: typeof i18nDict["ko"]["placeCard"];
  onOpen: () => void; canSetMeeting: boolean; done: boolean; busy: boolean; onSetMeeting: () => void;
}) {
  const isMe = place.kind === "me";
  return (
    <div className={`w-60 max-w-full overflow-hidden rounded-2xl border shadow-sm ${
      mine ? "border-sky-200 bg-sky-50" : "border-stone-200 bg-white"
    }`}>
      <button onClick={onOpen} className="flex w-full items-start gap-2 px-3 py-2.5 text-left transition-colors hover:bg-black/[0.02]">
        <span className="mt-0.5 flex-shrink-0 text-lg">{isMe ? "📍" : "🗺️"}</span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-bold text-stone-900">{place.name}</span>
          {place.address && <span className="mt-0.5 block truncate text-[11px] text-stone-400">{place.address}</span>}
          <span className="mt-1 block text-[11px] font-semibold text-sky-600">{labels.viewMap} →</span>
        </span>
      </button>
      {canSetMeeting && (
        <button onClick={onSetMeeting} disabled={done || busy}
          className="w-full border-t border-stone-100 py-2 text-xs font-semibold text-emerald-600 transition-colors hover:bg-emerald-50 disabled:text-stone-400 disabled:hover:bg-transparent">
          {done ? `✓ ${labels.meetingSet}` : `📌 ${labels.setAsMeeting}`}
        </button>
      )}
    </div>
  );
}

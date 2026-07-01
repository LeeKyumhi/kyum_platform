"use client";

import { useEffect, useState, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type Message = { id: number; senderId: number; senderName: string; content: string; createdAt: string };
type Me = { id: number; fullName: string };

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export default function ChatPage() {
  const params    = useParams();
  const router    = useRouter();
  const { t }     = useLanguage();
  const l         = t.chat;
  const bookingId = params.bookingId as string;

  const [me, setMe]             = useState<Me | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput]       = useState("");
  const [error, setError]       = useState("");
  const [connected, setConnected] = useState(false);

  const clientRef = useRef<Client | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef  = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const token = getToken();
    if (!token) { router.replace("/login"); return; }

    api<Message[]>(`/api/bookings/${bookingId}/messages`, { auth: true })
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
          client.subscribe(`/topic/bookings/${bookingId}`, (frame) => {
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
  }, [bookingId, router]);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages]);

  function onSend(e: React.FormEvent) {
    e.preventDefault();
    const content = input.trim();
    if (!content || !clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/bookings/${bookingId}/send`,
      body: JSON.stringify({ content }),
    });
    setInput(""); inputRef.current?.focus();
  }

  const myId = me?.id ?? null;
  const grouped = messages.reduce<Array<Message & { isFirst: boolean; isLast: boolean }>>((acc, msg, i) => {
    const prev = messages[i - 1], next = messages[i + 1];
    acc.push({ ...msg, isFirst: !prev || prev.senderId !== msg.senderId, isLast: !next || next.senderId !== msg.senderId });
    return acc;
  }, []);

  return (
    <main className="flex flex-col h-screen bg-gray-50">
      <header className="flex items-center gap-3 px-4 py-3 bg-white border-b border-gray-100 shadow-sm flex-shrink-0 pt-16">
        <button onClick={() => router.back()} className="btn-ghost text-sm">{l.back}</button>
        <div className="flex-1">
          <h1 className="font-semibold text-gray-900 text-sm">{l.title}</h1>
          <p className="text-xs text-gray-400">{l.booking} #{bookingId}</p>
        </div>
        <div className="flex items-center gap-1.5 text-xs">
          <span className={`h-2 w-2 rounded-full ${connected ? "bg-emerald-400" : "bg-gray-300"}`} />
          <span className={connected ? "text-emerald-600" : "text-gray-400"}>
            {connected ? l.connected : l.connecting}
          </span>
        </div>
      </header>

      {error && (
        <div className="mx-4 mt-2 rounded-xl bg-red-50 border border-red-100 px-3 py-2 text-sm text-red-600 flex-shrink-0">{error}</div>
      )}

      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-0.5">
        {messages.length === 0 && !error && (
          <div className="flex-1 flex flex-col items-center justify-center gap-2 text-center py-16">
            <span className="text-4xl">💬</span>
            <p className="text-gray-500 text-sm">{l.empty}</p>
            <p className="text-gray-400 text-xs">{l.emptySub}</p>
          </div>
        )}
        {grouped.map((m) => {
          const mine = m.senderId === myId;
          return (
            <div key={m.id} className={`flex items-end gap-2 ${mine ? "flex-row-reverse" : "flex-row"} ${m.isFirst ? "mt-3" : "mt-0.5"}`}>
              {!mine && (
                <div className={`w-7 h-7 rounded-full flex items-center justify-center text-white text-xs font-bold bg-gradient-to-br from-indigo-400 to-violet-500 flex-shrink-0 ${m.isLast ? "visible" : "invisible"}`}>
                  {m.senderName.slice(0, 1)}
                </div>
              )}
              <div className={`flex flex-col ${mine ? "items-end" : "items-start"} max-w-[72%]`}>
                {!mine && m.isFirst && <span className="text-xs text-gray-500 mb-1 ml-1">{m.senderName}</span>}
                <div className={`px-3.5 py-2.5 text-sm leading-relaxed ${
                  mine
                    ? `bg-indigo-600 text-white ${m.isFirst ? "rounded-t-2xl" : "rounded-t-lg"} rounded-bl-2xl ${m.isLast ? "rounded-br-sm" : "rounded-br-lg"}`
                    : `bg-white text-gray-800 shadow-sm border border-gray-100 ${m.isFirst ? "rounded-t-2xl" : "rounded-t-lg"} rounded-br-2xl ${m.isLast ? "rounded-bl-sm" : "rounded-bl-lg"}`
                }`}>
                  {m.content}
                </div>
                {m.isLast && <span className="text-[10px] text-gray-400 mt-1 mx-1">{formatTime(m.createdAt)}</span>}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={onSend} className="flex items-center gap-2 px-4 py-3 bg-white border-t border-gray-100 flex-shrink-0">
        <input ref={inputRef} value={input} onChange={(e) => setInput(e.target.value)}
          placeholder={l.placeholder}
          className="flex-1 rounded-full border border-gray-200 bg-gray-50 px-4 py-2.5 text-sm outline-none focus:border-indigo-300 focus:bg-white transition-all" />
        <button type="submit" disabled={!input.trim() || !connected}
          className="h-10 w-10 rounded-full bg-indigo-600 text-white flex items-center justify-center hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-all active:scale-95 flex-shrink-0"
          aria-label="Send">
          <svg className="w-4 h-4 rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
          </svg>
        </button>
      </form>
    </main>
  );
}

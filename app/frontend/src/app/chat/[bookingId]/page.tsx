"use client";

import { useParams } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import ChatRoom from "@/components/ChatRoom";

/** 예약 단위 채팅 — 실제 화면은 ChatRoom이 그린다 (예약 전 문의 /messages/[id]와 공유). */
export default function BookingChatPage() {
  const params = useParams();
  const { t } = useLanguage();
  const bookingId = params.bookingId as string;

  return (
    <ChatRoom
      historyPath={`/api/bookings/${bookingId}/messages`}
      translatePath={(mId) => `/api/bookings/${bookingId}/messages/${mId}/translate`}
      sendDestination={`/app/bookings/${bookingId}/send`}
      topic={`/topic/bookings/${bookingId}`}
      headerTitle={t.chat.title}
      headerSubtitle={`${t.chat.booking} #${bookingId}`}
    />
  );
}

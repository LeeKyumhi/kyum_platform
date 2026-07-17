"use client";

import { useParams } from "next/navigation";
import { useLanguage } from "@/context/LanguageContext";
import { api } from "@/lib/api";
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
      // 예약 채팅에서만 장소 카드에 "만남 장소로 지정" → Booking.meeting_place 저장
      onSetMeetingPlace={async (p) => {
        await api(`/api/bookings/${bookingId}/meeting-place`, {
          method: "PATCH", auth: true,
          body: { name: p.name, address: p.address ?? null, lat: p.lat, lng: p.lng, url: p.url ?? null },
        });
      }}
    />
  );
}

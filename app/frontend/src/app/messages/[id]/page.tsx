"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, getToken } from "@/lib/api";
import { useLanguage } from "@/context/LanguageContext";
import ChatRoom from "@/components/ChatRoom";
import { CalendarIcon } from "@/components/icons";

type Conversation = {
  id: number; guideProfileId: number; otherName: string;
  otherAvatarUrl: string | null; otherIsGuide: boolean;
};

/** 예약 전 1:1 문의 대화 — 헤더에 상대 이름을 띄우고 나머지는 ChatRoom 공용. */
export default function ConversationPage() {
  const params = useParams();
  const router = useRouter();
  const { t } = useLanguage();
  const id = params.id as string;

  const [conversation, setConversation] = useState<Conversation | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Conversation>(`/api/conversations/${id}`, { auth: true })
      .then(setConversation)
      .catch(() => router.replace("/messages"));
  }, [id, router]);

  // 상대가 가이드일 때(= 내가 여행자)만 예약 요청 CTA 노출
  const bookCta = conversation?.otherIsGuide ? (
    <Link
      href={`/guides/${conversation.guideProfileId}`}
      className="flex flex-shrink-0 items-center gap-1 rounded-full bg-sky-500 px-2.5 py-1 text-[11px] font-bold text-white transition-colors hover:bg-sky-600"
    >
      <CalendarIcon className="h-3.5 w-3.5" /> {t.dm.bookCta}
    </Link>
  ) : undefined;

  return (
    <ChatRoom
      historyPath={`/api/conversations/${id}/messages`}
      translatePath={(mId) => `/api/conversations/${id}/messages/${mId}/translate`}
      sendDestination={`/app/conversations/${id}/send`}
      topic={`/topic/conversations/${id}`}
      headerTitle={conversation?.otherName ?? t.dm.inboxTitle}
      headerSubtitle={conversation ? (conversation.otherIsGuide ? t.dm.guideBadge : t.dm.travelerBadge) : undefined}
      headerAction={bookCta}
    />
  );
}

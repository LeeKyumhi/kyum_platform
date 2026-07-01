"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, clearToken, getToken } from "@/lib/api";
import { clearMode } from "@/lib/mode";
import { useLanguage } from "@/context/LanguageContext";

type Me = { id: number; fullName: string; email: string };

export default function TravelerHome() {
  const router = useRouter();
  const { t } = useLanguage();
  const l = t.travelerHome;

  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    api<Me>("/api/users/me", { auth: true }).then(setMe).catch(() => router.replace("/login"));
  }, [router]);

  function onLogout() { clearToken(); clearMode(); router.push("/"); }

  const links = [
    { href: "/guides",               icon: "🔍", title: l.link1title, desc: l.link1desc },
    { href: "/traveler/bookings",    icon: "📋", title: l.link2title, desc: l.link2desc },
    { href: "/traveler/following",   icon: "❤️", title: t.personality.followingTitle, desc: t.personality.following },
    { href: "/traveler/profile",     icon: "✨", title: t.travelerProfile.link, desc: t.travelerProfile.linkDesc },
  ];

  return (
    <main className="page px-4">
      <div className="container-sm">
        <div className="flex items-center justify-between mb-6">
          <span className="badge-emerald py-1 px-3 text-sm">{l.badge}</span>
          <Link href="/select-mode" className="text-sm text-indigo-600 hover:underline font-medium">{l.switchMode}</Link>
        </div>
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">
            {l.greeting}{me ? `, ${me.fullName}` : ""}! 👋
          </h1>
          <p className="mt-1 text-gray-500 text-sm">{l.sub}</p>
        </div>
        <div className="flex flex-col gap-3 mb-8">
          {links.map((link) => (
            <Link key={link.href} href={link.href} className="card-hover p-5 flex items-center gap-4">
              <span className="text-2xl">{link.icon}</span>
              <div className="flex-1">
                <p className="font-semibold text-gray-900">{link.title}</p>
                <p className="text-xs text-gray-500 mt-0.5">{link.desc}</p>
              </div>
              <span className="text-gray-300">→</span>
            </Link>
          ))}
        </div>
        <div className="divider" />
        <button onClick={onLogout} className="w-full text-sm text-gray-400 hover:text-red-500 transition-colors py-2">
          {l.logout}
        </button>
      </div>
    </main>
  );
}

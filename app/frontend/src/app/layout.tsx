import type { Metadata } from "next";
import "./globals.css";
import { LanguageProvider } from "@/context/LanguageContext";
import Sidebar from "@/components/Sidebar";
import LanguagePicker from "@/components/LanguagePicker";

export const metadata: Metadata = {
  title: "PeerUp — Peer up with locals for your personal trip!",
  description: "Peer up with locals for your personal trip! Connect 1:1 with local Korean guides.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <LanguageProvider>
          <Sidebar />
          <LanguagePicker />
          {/* Offset for the fixed left rail (desktop) + mobile top/bottom bars */}
          <div className="md:pl-64">{children}</div>
        </LanguageProvider>
      </body>
    </html>
  );
}

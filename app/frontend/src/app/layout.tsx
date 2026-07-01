import type { Metadata } from "next";
import "./globals.css";
import { LanguageProvider } from "@/context/LanguageContext";
import Navbar from "@/components/Navbar";
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
          <Navbar />
          <LanguagePicker />
          {children}
        </LanguageProvider>
      </body>
    </html>
  );
}

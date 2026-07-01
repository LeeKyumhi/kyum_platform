"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { type Lang, getSavedLang, saveLang, translations } from "@/lib/i18n";

type LanguageContextValue = {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: typeof translations.ko;
  showPicker: boolean;
  dismissPicker: () => void;
};

const LanguageContext = createContext<LanguageContextValue | null>(null);

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  // Default to "ko" so SSR and first render match — no blank flash
  const [lang, setLangState] = useState<Lang>("ko");
  const [showPicker, setShowPicker] = useState(false);

  useEffect(() => {
    const saved = getSavedLang();
    if (saved) {
      setLangState(saved);
    } else {
      // No preference stored → show picker after hydration
      setShowPicker(true);
    }
  }, []);

  function setLang(l: Lang) {
    setLangState(l);
    saveLang(l);
    setShowPicker(false);
  }

  function dismissPicker() {
    saveLang(lang);
    setShowPicker(false);
  }

  return (
    <LanguageContext.Provider value={{ lang, setLang, t: translations[lang], showPicker, dismissPicker }}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage() {
  const ctx = useContext(LanguageContext);
  if (!ctx) throw new Error("useLanguage must be used inside LanguageProvider");
  return ctx;
}

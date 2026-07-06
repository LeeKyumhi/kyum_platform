/**
 * Tiny inline-SVG icon set shared by the redesigned pages.
 * Pure presentational components — no state, no client hooks, no dependencies.
 * All icons inherit color via `currentColor`; size via className.
 */

type IconProps = { className?: string };

export function SearchIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={2.2} aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path strokeLinecap="round" d="M20.5 20.5 16 16" />
    </svg>
  );
}

export function StarIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path d="M12 2.5l2.83 6.02 6.6.84-4.86 4.57 1.25 6.53L12 17.3l-5.82 3.16 1.25-6.53-4.86-4.57 6.6-.84L12 2.5z" />
    </svg>
  );
}

export function PinIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 21.5S5 15.6 5 10.2a7 7 0 1 1 14 0c0 5.4-7 11.3-7 11.3Z" />
      <circle cx="12" cy="10" r="2.6" />
    </svg>
  );
}

export function HeartIcon({ className = "h-5 w-5", filled = false }: IconProps & { filled?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill={filled ? "currentColor" : "none"} stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 8.7c0 5.2-7.2 9.8-9 10.8-1.8-1-9-5.6-9-10.8a4.9 4.9 0 0 1 4.9-4.9c1.7 0 3.2.8 4.1 2.1a5 5 0 0 1 4.1-2.1A4.9 4.9 0 0 1 21 8.7Z" />
    </svg>
  );
}

export function ChatIcon({ className = "h-5 w-5" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 11.8c0 4.3-4 7.8-9 7.8-1.4 0-2.7-.3-3.9-.8L3 20l1.4-3.7A7.2 7.2 0 0 1 3 11.8C3 7.5 7 4 12 4s9 3.5 9 7.8Z" />
    </svg>
  );
}

export function EyeIcon({ className = "h-5 w-5" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function CheckBadgeIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M8.6 3.8a2.3 2.3 0 0 1 6.8 0l.3.3a2.3 2.3 0 0 0 1.6.7h.4a2.3 2.3 0 0 1 2.3 2.3v.4c0 .6.2 1.2.7 1.6l.3.3a2.3 2.3 0 0 1 0 3.2l-.3.3a2.3 2.3 0 0 0-.7 1.6v.4a2.3 2.3 0 0 1-2.3 2.3h-.4a2.3 2.3 0 0 0-1.6.7l-.3.3a2.3 2.3 0 0 1-3.2 0l-.3-.3a2.3 2.3 0 0 0-1.6-.7h-.4a2.3 2.3 0 0 1-2.3-2.3v-.4a2.3 2.3 0 0 0-.7-1.6l-.3-.3a2.3 2.3 0 0 1 0-3.2l.3-.3c.5-.4.7-1 .7-1.6v-.4a2.3 2.3 0 0 1 2.3-2.3h.4a2.3 2.3 0 0 0 1.6-.7l.3-.3Zm7 6.3a.9.9 0 0 0-1.3-1.2l-3.4 3.7-1.6-1.6a.9.9 0 1 0-1.2 1.3l2.2 2.2c.4.4 1 .3 1.3 0l4-4.4Z"
        clipRule="evenodd"
      />
    </svg>
  );
}

export function CalendarIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path strokeLinecap="round" d="M3.5 9.5h17M8 3v4M16 3v4" />
    </svg>
  );
}

export function UsersIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <circle cx="9" cy="8" r="3.5" />
      <path strokeLinecap="round" d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.9a3.5 3.5 0 0 1 0 6.2M17.5 13.9a6.5 6.5 0 0 1 4 6.1" />
    </svg>
  );
}

export function GlobeIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3a14.5 14.5 0 0 1 0 18M12 3a14.5 14.5 0 0 0 0 18" />
    </svg>
  );
}

export function CompassIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path strokeLinecap="round" strokeLinejoin="round" d="m15.5 8.5-2 5-5 2 2-5 5-2Z" />
    </svg>
  );
}

export function ChevronLeftIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={2.2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="m14.5 5.5-6.5 6.5 6.5 6.5" />
    </svg>
  );
}

export function CameraIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.2l.9-1.5c.3-.6.9-1 1.6-1h3.8c.7 0 1.3.4 1.6 1l.9 1.5h1.2A2.4 2.4 0 0 1 20 8.4v8.9a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 17.3V8.4Z" />
      <circle cx="12" cy="12.8" r="3.3" />
    </svg>
  );
}

export function ArrowRightIcon({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={2.2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12h15m0 0-6-6m6 6-6 6" />
    </svg>
  );
}

export type InterestKey =
  | "FOOD" | "CAFE" | "STREET_FOOD" | "DESSERT" | "NIGHTLIFE"
  | "HISTORY" | "MUSEUM" | "ART" | "MOVIE" | "BOOKSTORE"
  | "KPOP" | "LIVE_MUSIC" | "SPORTS" | "GAMING" | "FESTIVAL"
  | "SHOPPING" | "FASHION" | "MARKET" | "BEAUTY"
  | "NATURE" | "HIKING" | "CYCLING" | "BEACH" | "PHOTO"
  | "NIGHTVIEW" | "ARCHITECTURE" | "LOCAL_LIFE" | "WELLNESS" | "HANOK";

export type CategoryKey = "food" | "culture" | "enter" | "shop" | "nature" | "city";

export const INTEREST_CATEGORIES: { key: CategoryKey; interests: InterestKey[] }[] = [
  { key: "food",    interests: ["FOOD", "CAFE", "STREET_FOOD", "DESSERT", "NIGHTLIFE"] },
  { key: "culture", interests: ["HISTORY", "MUSEUM", "ART", "MOVIE", "BOOKSTORE"] },
  { key: "enter",   interests: ["KPOP", "LIVE_MUSIC", "SPORTS", "GAMING", "FESTIVAL"] },
  { key: "shop",    interests: ["SHOPPING", "FASHION", "MARKET", "BEAUTY"] },
  { key: "nature",  interests: ["NATURE", "HIKING", "CYCLING", "BEACH", "PHOTO"] },
  { key: "city",    interests: ["NIGHTVIEW", "ARCHITECTURE", "LOCAL_LIFE", "WELLNESS", "HANOK"] },
];

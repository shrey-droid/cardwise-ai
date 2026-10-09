export type Spending = {
  groceries: number;
  gas: number;
  dining: number;
  travel: number;
  other: number;
  transit: number;
  rideshare: number;
  evCharging: number;
};

export const SPENDING_CATEGORIES: (keyof Spending)[] = [
  "groceries",
  "gas",
  "dining",
  "travel",
  "other",
  "transit",
  "rideshare",
  "evCharging",
];

export const BACKEND_SPENDING_CATEGORIES = [
  "GROCERIES",
  "GAS",
  "DINING",
  "TRAVEL",
  "OTHER",
  "TRANSIT",
  "RIDESHARE",
  "EV_CHARGING",
] as const;

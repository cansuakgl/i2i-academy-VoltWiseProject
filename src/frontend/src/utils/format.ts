export function formatWatts(value: number | string | null | undefined) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) return "—";
  return `${numericValue.toFixed(0)} W`;
}

export function formatKwh(value: number | string | null | undefined) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) return "—";
  return `${numericValue.toFixed(2)} kWh`;
}

export function formatMoney(value: number | string | null | undefined) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) return "—";
  return `${numericValue.toFixed(2)} TRY`;
}

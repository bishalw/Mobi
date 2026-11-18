// Client-side copies of the API's limits, so mistakes show inline instead of as a 400.

// A cost: more than 0, at most 2 decimal places.
export const isMoney = (value: string) => /^\d{1,10}(\.\d{1,2})?$/.test(value.trim()) && Number(value) > 0

// A whole quantity between min and 100,000 (the API's cap on order, shipment and receipt lines).
export const isQuantity = (value: string, min = 1) => {
  if (value.trim() === '') return false
  const n = Number(value)
  return Number.isInteger(n) && n >= min && n <= 100_000
}

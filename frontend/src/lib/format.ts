const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const date = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export const formatCurrency = (value: number) => currency.format(value)
export const formatDate = (iso: string) => date.format(new Date(iso))

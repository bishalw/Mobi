import { useEffect, useRef, type RefObject } from 'react'
import { X } from 'lucide-react'

export const inputClass = 'w-full rounded-md border border-gray-200 px-3 py-2 text-sm outline-none focus:border-gray-400'

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Keyboard behaviour every dialog needs: Escape closes it, focus moves into it on open,
 * Tab stays inside it, and focus returns to whatever opened it on close.
 */
export function useDialogKeyboard(ref: RefObject<HTMLElement | null>, onClose: () => void) {
  const close = useRef(onClose)
  close.current = onClose

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const dialog = ref.current
    const focusables = () => Array.from(dialog?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])
    // First form field if there is one, else the first control (the close button).
    const first = focusables().find((el) => el.matches('input, select, textarea')) ?? focusables()[0]
    first?.focus()

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        close.current()
        return
      }
      if (e.key !== 'Tab') return
      const items = focusables()
      if (items.length === 0) return
      const head = items[0]
      const tail = items[items.length - 1]
      if (e.shiftKey && document.activeElement === head) {
        e.preventDefault()
        tail.focus()
      } else if (!e.shiftKey && document.activeElement === tail) {
        e.preventDefault()
        head.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      opener?.focus()
    }
  }, [ref])
}

export default function Modal({ title, onClose, children, wide = false }: {
  title: string
  onClose: () => void
  children: React.ReactNode
  wide?: boolean
}) {
  const ref = useRef<HTMLDivElement>(null)
  useDialogKeyboard(ref, onClose)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/30 px-4 py-8">
      <div ref={ref} role="dialog" aria-modal="true" aria-label={title}
        className={`w-full ${wide ? 'max-w-2xl' : 'max-w-md'} rounded-lg border border-gray-200 bg-white p-6 shadow-lg`}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">{title}</h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700">
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

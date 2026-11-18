import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, ShieldCheck, User as UserIcon, Ban, CheckCircle2, Lock, LockOpen, Warehouse, Truck } from 'lucide-react'
import toast from 'react-hot-toast'
import { getUsers, unlockUser, updateUserRole, updateUserStatus } from '@/api/admin'
import { formatDate } from '@/lib/format'
import { useAuthStore } from '@/stores/authStore'
import type { AdminUser } from '@/types'
import { ROLES, asRole, roleLabel, type Role } from '@/lib/roles'

const PAGE_SIZE = 25

// Surfaces the message the API sent rather than a generic one, because the useful
// failures here are deliberate refusals — demoting yourself, or an unknown role.
function apiMessage(err: unknown, fallback: string) {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message && message.trim().length > 0 ? message : fallback
}

export default function UsersPage() {
  const [search, setSearch] = useState('')
  const [term, setTerm] = useState('')
  const [page, setPage] = useState(0)
  const currentUserId = useAuthStore((s) => s.userId)
  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', term, page],
    queryFn: () => getUsers({ page, size: PAGE_SIZE, search: term || undefined }),
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['admin-users'] })
    // The customer view is built from the same accounts, so it goes stale too.
    qc.invalidateQueries({ queryKey: ['admin-customers'] })
  }

  const roleMutation = useMutation({
    mutationFn: ({ id, role, supplierId }: { id: number; role: string; supplierId?: number }) =>
      updateUserRole(id, role, supplierId),
    onSuccess: (user) => {
      invalidate()
      const label = asRole(user.role) ? roleLabel[asRole(user.role)!] : user.role
      toast.success(`${user.username} is now ${label}. They need to sign in again.`)
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not change role')),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => updateUserStatus(id, enabled),
    onSuccess: (user) => {
      invalidate()
      toast.success(`${user.username} ${user.enabled ? 'enabled' : 'disabled'}`)
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not change account status')),
  })

  const unlockMutation = useMutation({
    mutationFn: (id: number) => unlockUser(id),
    onSuccess: (user) => {
      invalidate()
      toast.success(`${user.username} can sign in again`)
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not unlock account')),
  })

  const users = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  function submitSearch(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setTerm(search.trim())
  }

  return (
    <div>
      <div className="mb-6 flex items-end justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Users</h1>
          <p className="mt-0.5 text-sm text-gray-500">
            {data ? `${data.totalElements} registered account${data.totalElements === 1 ? '' : 's'}` : 'Loading…'}
          </p>
        </div>

        <form onSubmit={submitSearch} className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search username or email"
            className="w-64 rounded-md border border-gray-200 py-2 pl-9 pr-3 text-sm outline-none focus:border-gray-400"
          />
        </form>
      </div>

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">User</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Role</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Signed up</th>
              <th className="px-4 py-3">Last login</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-400">
                  Loading users…
                </td>
              </tr>
            )}

            {!isLoading && users.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-400">
                  {term ? `No users matching "${term}".` : 'No users yet.'}
                </td>
              </tr>
            )}

            {users.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                isSelf={user.id === currentUserId}
                busy={roleMutation.isPending || statusMutation.isPending || unlockMutation.isPending}
                onRoleChange={(role, supplierId) => roleMutation.mutate({ id: user.id, role, supplierId })}
                onStatusChange={(enabled) => statusMutation.mutate({ id: user.id, enabled })}
                onUnlock={() => unlockMutation.mutate(user.id)}
              />
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <span className="text-gray-500">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={page + 1 >= totalPages}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

interface RowProps {
  user: AdminUser
  isSelf: boolean
  busy: boolean
  onRoleChange: (role: Role, supplierId?: number) => void
  onStatusChange: (enabled: boolean) => void
  onUnlock: () => void
}

const roleBadge: Record<Role, { style: string; Icon: typeof UserIcon }> = {
  ADMIN: { style: 'bg-indigo-100 text-indigo-700', Icon: ShieldCheck },
  WAREHOUSE: { style: 'bg-sky-100 text-sky-700', Icon: Warehouse },
  SUPPLIER: { style: 'bg-orange-100 text-orange-700', Icon: Truck },
  USER: { style: 'bg-gray-100 text-gray-600', Icon: UserIcon },
}

function UserRow({ user, isSelf, busy, onRoleChange, onStatusChange, onUnlock }: RowProps) {
  const role = asRole(user.role)
  const badge = roleBadge[role ?? 'USER']

  function changeRole(next: Role) {
    if (next === role) return
    if (next === 'SUPPLIER') {
      // Until supplier management exists the id is entered by hand;
      // 1 is the seeded "Mobi Distribution".
      const answer = window.prompt('Supplier id this account acts for', String(user.supplierId ?? 1))
      const supplierId = Number(answer)
      if (!answer || !Number.isInteger(supplierId) || supplierId < 1) return
      onRoleChange(next, supplierId)
      return
    }
    onRoleChange(next)
  }
  const isLocked = !!user.lockedUntil

  return (
    <tr className={`hover:bg-gray-50 ${user.enabled ? '' : 'bg-gray-50/60'}`}>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="font-medium text-gray-900">{user.username}</span>
          {isSelf && <span className="text-xs text-gray-400">(you)</span>}
        </div>
      </td>
      <td className="px-4 py-3 text-gray-500">{user.email ?? <span className="text-gray-300">—</span>}</td>
      <td className="px-4 py-3">
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${badge.style}`}>
          <badge.Icon size={11} />
          {role ? roleLabel[role] : user.role}
          {role === 'SUPPLIER' && user.supplierId != null && (
            <span className="font-normal opacity-70">#{user.supplierId}</span>
          )}
        </span>
      </td>
      <td className="px-4 py-3">
        <span
          className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
            user.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
          }`}
        >
          {user.enabled ? 'Active' : 'Disabled'}
        </span>
        {isLocked && (
          <span
            title={`Too many wrong passwords. Locked until ${formatDate(user.lockedUntil!)}`}
            className="ml-1.5 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700"
          >
            <Lock size={10} />
            Locked
          </span>
        )}
      </td>
      <td className="px-4 py-3 text-xs text-gray-500">{formatDate(user.createdAt)}</td>
      <td className="px-4 py-3 text-xs text-gray-400">
        {user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Never'}
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center justify-end gap-2">
          {/* Unlocking is allowed on any row, including your own (another admin may be
              doing it for you). */}
          {isLocked && (
            <button
              onClick={onUnlock}
              disabled={busy}
              className="inline-flex items-center gap-1 rounded-md border border-amber-200 px-2.5 py-1 text-xs text-amber-700 hover:bg-amber-50 disabled:opacity-40"
            >
              <LockOpen size={11} />
              Unlock
            </button>
          )}
          {/* Role and status actions are hidden for your own row. The server refuses them anyway —
              this just avoids offering a button that can only fail. */}
          {!isSelf && (
            <>
              <select
                aria-label={`Role for ${user.username}`}
                value={role ?? ''}
                onChange={(e) => changeRole(e.target.value as Role)}
                disabled={busy}
                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {roleLabel[r]}
                  </option>
                ))}
              </select>
              <button
                onClick={() => onStatusChange(!user.enabled)}
                disabled={busy}
                className={`inline-flex items-center gap-1 rounded-md border px-2.5 py-1 text-xs disabled:opacity-40 ${
                  user.enabled
                    ? 'border-red-200 text-red-600 hover:bg-red-50'
                    : 'border-green-200 text-green-700 hover:bg-green-50'
                }`}
              >
                {user.enabled ? <Ban size={11} /> : <CheckCircle2 size={11} />}
                {user.enabled ? 'Disable' : 'Enable'}
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  )
}

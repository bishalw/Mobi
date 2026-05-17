# Mobi Frontend

React + TypeScript operations console for the Mobi phone store platform.

## Stack

| Tool | Purpose |
| --- | --- |
| Vite | Dev server and build tooling |
| React 19 | UI framework |
| TypeScript | Type safety |
| Tailwind CSS | Styling |
| TanStack Query | Data fetching, caching, loading states |
| React Router v7 | Client-side routing |
| Zustand | Auth and cart state |
| Zod + React Hook Form | Form validation |
| Axios | HTTP client with JWT interceptor |
| Lucide React | Icons |

## Running Locally

The backend must be running first (see root README). Then:

```bash
cd frontend
npm install
npm run dev
```

App runs at `http://localhost:5173`. API calls are proxied to `http://localhost` (the Kubernetes ingress).

## User Roles

| Role | Access |
| --- | --- |
| User | Browse products, add to cart, place and cancel own orders |
| Admin | All of the above + manage products, view inventory, view all orders, confirm orders |

The default admin account is seeded at startup via `ADMIN_BOOTSTRAP_*` environment variables (see root README).

## App Structure

```
src/
├── api/           # typed fetch functions per service
├── app/           # router and query client
├── components/    # layout and cart drawer
├── features/      # auth, products, inventory, orders
├── lib/           # token store and formatters
├── stores/        # auth and cart Zustand stores
└── types/         # TypeScript types matching backend DTOs
```

## Production Build

```bash
npm run build
```

Or build the Docker image:

```bash
docker build -t mobi/frontend:local .
```

The image serves the built app via nginx with SPA fallback routing.

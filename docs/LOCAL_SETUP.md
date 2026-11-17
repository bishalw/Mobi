# Running Mobi locally

This guide takes you from a fresh laptop to the whole system running on your
machine: seven Spring Boot services, MySQL, Redis, Kafka, the Envoy edge proxy, a
trace viewer and a test email inbox, all inside a local Kubernetes cluster, with the
React app on top.

Plan for about **45 minutes** the first time, most of it waiting for builds.
Afterwards, starting again takes a few minutes.

**Contents**

1. [Install the tools](#1-install-the-tools)
2. [Give Docker enough memory](#2-give-docker-enough-memory)
3. [Get the code](#3-get-the-code)
4. [Create your local settings file](#4-create-your-local-settings-file)
5. [Create the cluster](#5-create-the-cluster)
6. [Build the services](#6-build-the-services)
7. [Deploy](#7-deploy)
8. [Check that it works](#8-check-that-it-works)
9. [Load demo data](#9-load-demo-data)
10. [Start the web app](#10-start-the-web-app)
11. [Try the main flows](#11-try-the-main-flows)
12. [Look inside: traces, emails, logs](#12-look-inside-traces-emails-logs)
13. [Everyday workflow](#13-everyday-workflow)
14. [Stopping, resetting, removing](#14-stopping-resetting-removing)
15. [Troubleshooting](#15-troubleshooting)

---

## What you are running

```text
Browser ── http://localhost:5173 (Vite dev server, the React app)
   │
   └─ /auth, /productapi, ... ──► http://localhost:80
                                    │
                         kind cluster "mobi" (Docker container)
                                    │
                         Envoy Gateway (edge: rate limit, retries)
                                    │
                         api-gateway (routing, per-user rate limits)
                                    │
     ┌──────────┬───────────┬───────┴─────┬───────────┬──────────────┐
   auth     product     inventory       order      payment     notification
     └──────────┴───────────┴──── MySQL, Redis, Kafka ┴───────────┴──────────────┘
                                 Jaeger (traces), Mailpit (emails)
```

Everything except the React app runs inside **kind** (Kubernetes in Docker), a
real Kubernetes cluster in a single Docker container. It is deployed with the same
Helm chart that the cloud environments use.

---

## 1. Install the tools

macOS with [Homebrew](https://brew.sh):

```bash
brew install --cask docker      # Docker Desktop (or use OrbStack / Colima)
brew install kind kubectl helm node git openssl
```

Linux: install Docker Engine, then
[kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation),
[kubectl](https://kubernetes.io/docs/tasks/tools/),
[Helm](https://helm.sh/docs/intro/install/) and Node.js from your package manager
or their sites.

Check them:

```bash
docker --version     # any recent version
kind --version       # 0.20 or newer
kubectl version --client
helm version         # v3
node --version       # v20 or newer
openssl version
```

You do **not** need Java or Maven to run the stack: services are compiled inside
Docker. Install Java 21 only to run tests or services from an IDE (see
[Everyday workflow](#13-everyday-workflow)).

## 2. Give Docker enough memory

Seven JVMs plus MySQL, Kafka and Redis need room. In **Docker Desktop → Settings →
Resources**, set:

| Setting | Minimum | Comfortable |
|---|---|---|
| Memory | 8 GB | 12 GB |
| CPUs | 4 | 6+ |
| Disk | 30 GB free | 50 GB |

Apply and restart Docker. With too little memory, pods crash and restart in a loop
(`OOMKilled`) and the deploy times out.

Also make sure nothing else on your machine is using **ports 80 and 443** (a local
nginx or Apache, another kind cluster, etc.). The cluster takes both.

```bash
lsof -nP -iTCP:80 -sTCP:LISTEN; lsof -nP -iTCP:443 -sTCP:LISTEN   # should print nothing
```

## 3. Get the code

```bash
git clone <repo-url> Mobi
cd Mobi
```

All commands below run from the repository root unless they say otherwise.

## 4. Create your local settings file

Passwords and keys for your local stack live in `.env.local`. It is gitignored and
never leaves your machine.

```bash
cp .env.example .env.local

# Replace every "replace-with-..." placeholder with a random value.
perl -pi -e 's/replace-with-[\w-]+/join "", map { sprintf "%02x", int rand 256 } 1..16/ge' .env.local
```

Then open `.env.local` and set **`ADMIN_BOOTSTRAP_PASSWORD`** to something you will
remember. That is the password of the `admin` account created on first start.

Three demo staff accounts are created (or reset) on every start:

| Login | Password (`.env.local`) | Role |
| --- | --- | --- |
| `admin` | `ADMIN_BOOTSTRAP_PASSWORD` | Admin: everything |
| `warehouse` | `WAREHOUSE_BOOTSTRAP_PASSWORD` (default `warehousepass`) | Warehouse: stock and receiving |
| `supplier` | `SUPPLIER_BOOTSTRAP_PASSWORD` (default `supplierpass`) | Supplier "Mobi Distribution": its purchase orders |

Customers register themselves (or use `testuser` from the seed script).

Leave these two **empty**. The deploy script generates them on first run and saves
them back into `.env.local`:

| Key | What it is |
|---|---|
| `JWT_PRIVATE_KEY` | RSA key auth-service signs login tokens with |
| `ORDER_SERVICE_CLIENT_SECRET` | order-service's password for getting its own service token |

Keep the generated values: if they change, everyone who is signed in is signed out.

## 5. Create the cluster

Start Docker Desktop and wait until it reports it is running, then:

```bash
./scripts/kind-up.sh
```

This takes about 2 to 3 minutes and does the following:

- creates a kind cluster named `mobi` and maps your `localhost:80/443` into it;
- adds the Bitnami Helm repository (MySQL, Redis, Kafka charts);
- installs **Envoy Gateway**, the edge proxy, which implements the Kubernetes Gateway API;
- creates the `mobi` namespace.

Check:

```bash
kubectl get nodes                    # mobi-control-plane   Ready
kubectl get gatewayclass eg          # ACCEPTED   True
```

You only do this once. The cluster survives Docker restarts.

## 6. Build the services

```bash
./scripts/kind-load-images.sh
```

This builds a Docker image for each of the seven services (Maven runs inside
Docker) and loads them into the cluster. **The first run takes 10 to 20 minutes**
while Maven downloads dependencies; later runs are much faster.

Run it again whenever you change backend code (or rebuild just one service, see
[Everyday workflow](#13-everyday-workflow)).

## 7. Deploy

```bash
./scripts/deploy.sh local
```

This takes 5 to 10 minutes the first time. Steps, in order:

1. Reads `.env.local` and generates the signing key and service secret if missing.
2. Creates two Kubernetes Secrets: `mobi-secrets` (database passwords, admin account)
   and `mobi-auth-secrets` (signing key and service secret). Only the services that
   need the second one can read it.
3. Installs **MySQL** (one database per service), **Redis** and **Kafka** in the
   cluster, plus **Jaeger** (traces) and **Mailpit** (test inbox).
4. Installs the **Mobi Helm chart**: the seven services, the Gateway and its routes.
5. Waits until every service reports ready.

It ends with:

```text
Mobi deployment to local is ready

  API             http://localhost
  Traces          kubectl -n mobi port-forward svc/jaeger 16686:16686   then http://localhost:16686
  Email inbox     kubectl -n mobi port-forward svc/mailpit 8025:8025    then http://localhost:8025
```

The JVMs start slowly on a laptop. Deploying again is safe at any time and only
changes what changed.

## 8. Check that it works

```bash
kubectl get pods -n mobi
```

Every pod should be `Running` with `1/1` ready. Then:

```bash
curl -s http://localhost/productapi/products        # [] on a fresh install
```

Now run the end-to-end smoke test:

```bash
./scripts/k8s-smoke.sh
```

It signs up a user, tests sessions and refresh tokens, creates a product, places and
pays for orders (including a declined card), refunds as admin, locks and unlocks an
account, and signs out everywhere. Every line should start with `[PASS]`, ending
with `All smoke checks passed.`

## 9. Load demo data

```bash
./scripts/seed-phones.sh
```

Creates a small phone catalogue with stock, a customer **`testuser` / `testpass123`**
and a few orders. It signs in with the admin account from `.env.local`.

## 10. Start the web app

In a second terminal:

```bash
cd frontend
npm install        # first time only
npm run dev
```

Open **http://localhost:5173**. The dev server forwards API calls to the cluster on
`http://localhost`. To point it at an api-gateway started from your IDE instead,
create `frontend/.env.local` with `VITE_API_TARGET=http://localhost:8080`.

## 11. Try the main flows

Sign in as **admin** (password from `.env.local`) in one browser, and as
**testuser** (or a newly registered account) in a private window.

**As a customer**

1. Browse **Products**, add items to the cart, and check out. The order shows as
   *Awaiting payment*: stock is held for you.
2. Open the order and pay with a test card:

   | Card | Result |
   |---|---|
   | Visa •••• 4242 | succeeds |
   | Mastercard •••• 4444 | succeeds |
   | Visa •••• 0002 | declined; try another card |
   | Visa •••• 9995 | insufficient funds |
   | Provider outage | payment service unavailable (503) |

   No real card network is involved.
3. Check the email for each step in Mailpit (section 12).
4. **Sign out everywhere** (sidebar) ends every session of the account, on all devices.

**As admin**

- **Dashboard**: order and revenue totals.
- **Products**: edit the catalogue.
- **Inventory**: add opening stock, adjust stock (count corrections, damaged or lost
  items, each with a note), set shelf location and reorder level, and see each product's
  change history.
- **Suppliers**: add suppliers and set each one's **Catalogue**: which products they sell
  and the unit cost.
- **Purchase orders**: raise a draft for a supplier (products come from its catalogue),
  edit it, **Send to supplier**, **Cancel PO**, or **Close short** once part of it has
  arrived. The seed data already sent one to Mobi Distribution.

- **Receiving**: same as the warehouse view below.

**As warehouse**

- **Receiving**: purchase orders waiting to be counted in, soonest arrival first.
  **Receive** opens the count: good and damaged per line, defaulting to what's
  outstanding. Good units appear on the Inventory page within seconds; damaged ones stay
  on the PO.
- **Inventory**: as for admin.
- **Purchase orders**: read-only, without costs.
- Shipment notices from suppliers arrive by email (Mailpit, as `warehouse@mobi.local`).

**As supplier** (Mobi Distribution)

- **My purchase orders**: orders sent to you. **Accept** with a delivery date or
  **Reject** with a reason; then **Send shipment notice** with carrier, tracking and
  quantities. Received and damaged counts show per line once the warehouse counts them.
- New purchase orders arrive by email at `orders@mobi-distribution.test` (Mailpit).

**The whole loop**: as admin, send a PO (the seed data already sent one) → as supplier,
accept it and send a shipment notice → as warehouse, receive it on **Receiving** → stock
goes up on **Inventory**. Admins get emails when the supplier answers, and a low-stock
email when a product drops to its reorder level.
- **Orders**: all orders. On a paid order, **Refund order** returns the money; tick
  *Put items back in stock* if the goods came back.
- **Payments**: every payment attempt, with refunds.
- **Users**: change roles, disable accounts, and **Unlock** accounts locked after 5
  wrong passwords.
- **Customers**: per-customer order history and spend.

## 12. Look inside: traces, emails, logs

Run each port-forward in its own terminal. Each one runs until you press Ctrl+C.

**Emails (Mailpit).** Notifications are sent over real SMTP to a test inbox:

```bash
kubectl -n mobi port-forward svc/mailpit 8025:8025
```

Open http://localhost:8025 to see welcome, order and payment emails. SMS messages
are written to notification-service's log rather than sent.

**Real emails instead of Mailpit (optional).** To see emails in a real inbox, add an SMTP
relay to `.env.local` and redeploy with `scripts/deploy.sh local`. Setting `MAIL_HOST`
is the switch; remove it to go back to Mailpit.

```bash
# Gmail: needs 2-Step Verification and an App Password (myaccount.google.com/apppasswords)
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=your-16-character-app-password
MAIL_FROM="Mobi <you@gmail.com>"

# Or the staging SES account (sandbox: only delivers to addresses verified in SES)
# MAIL_HOST=email-smtp.us-east-1.amazonaws.com
# MAIL_USERNAME / MAIL_PASSWORD from: terraform -chdir=infra/terraform/envs/aws-staging output -raw ses_smtp_username (and ses_smtp_password)
# MAIL_FROM="Mobi <no-reply@staging.mobi.example.com>"
```

Port 587 with STARTTLS and authentication is the default; override with `MAIL_PORT`,
`MAIL_STARTTLS` and `MAIL_SMTP_AUTH`. Then register an account with your own address to
get the welcome email. The seed accounts (admin, warehouse, supplier) have made-up
addresses: with Gmail those bounce, and in the SES sandbox they are refused and recorded
as skipped (`address rejected`), so staff emails only arrive if you change those users'
emails to real ones.

**Traces (Jaeger).** Every request is traced across services, the outbox and Kafka:

```bash
kubectl -n mobi port-forward svc/jaeger 16686:16686
```

Open http://localhost:16686, pick `order-service` and find a `POST /orderapi/orders/{id}/pay`
to see the whole checkout: order → payment → inventory → Kafka → notification.

**Logs**

```bash
kubectl -n mobi logs deploy/order-service -f            # follow one service
kubectl -n mobi logs deploy/order-service --previous    # why the last one crashed
```

Every log line carries `[service,correlationId]` plus the trace and span ids, so a
line can be looked up in Jaeger.

**Metrics**: each service serves Prometheus metrics at `/actuator/prometheus`
(inside the cluster).

## 13. Everyday workflow

**Changed one backend service?** Rebuild just that one and restart it:

```bash
SVC=order-service
docker build -t mobi/$SVC:local ./$SVC
kind load docker-image mobi/$SVC:local --name mobi
kubectl -n mobi rollout restart deployment/$SVC
kubectl -n mobi rollout status deployment/$SVC
```

**Changed the Helm chart or settings?** Run `./scripts/deploy.sh local` again.

**Changed the frontend?** Nothing to do: `npm run dev` reloads in the browser.

**Running tests** (needs Java 21):

```bash
cd order-service && ./mvnw test           # one service
cd frontend && npm run build              # typecheck and build the UI
```

**Database migrations** run automatically (Flyway) when a service starts. Add a new
`V<n>__description.sql` in the service's `src/main/resources/db/migration`.
Never edit one that has already run.

**Connecting to MySQL**:

```bash
kubectl -n mobi exec -it statefulset/mysql -- mysql -uroot -p"$(grep ^MYSQL_ROOT_PASSWORD .env.local | cut -d= -f2)"
```

## 14. Stopping, resetting, removing

| You want to | Do |
|---|---|
| Pause everything | Quit Docker Desktop. The cluster and data come back when it restarts. |
| Restart one service | `kubectl -n mobi rollout restart deployment/<service>` |
| Wipe all data, keep the cluster | `helm -n mobi uninstall mobi mysql redis kafka`, then `kubectl -n mobi delete pvc --all`, then `./scripts/deploy.sh local` |
| Remove everything | `kind delete cluster --name mobi` (then start again from step 5) |

## 15. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `kind-up.sh`: *port 80 is already allocated* | Something else is using port 80 or 443. Stop it, or delete an old kind cluster (`kind get clusters`). |
| `deploy.sh` says it is *replacing Bitnami MySQL 8.0 with MySQL 8.4* | One-time move. It deletes the old database and its volume, then re-seeds the demo data at the end. Orders and accounts you created by hand are gone. |
| `procurement-service` keeps restarting on a cluster created before it existed | Its database is created by `./scripts/deploy.sh local`; re-run it rather than only restarting the pod. |
| `deploy.sh`: *missing required environment value: X* | `X` is empty in `.env.local`. Fill it in (step 4). |
| `deploy.sh`: *GatewayClass 'eg' not found* | Envoy Gateway is not installed. Run `./scripts/install-envoy-gateway.sh` (or `kind-up.sh` again). |
| `deploy.sh` times out waiting for a service | Usually memory. `kubectl -n mobi get pods`, then `kubectl -n mobi describe pod <name>`. Look for `OOMKilled`; give Docker more memory (step 2). |
| Pod in `ImagePullBackOff` / `ErrImageNeverPull` | The image was never loaded into kind. Run `./scripts/kind-load-images.sh`. |
| Pod in `CrashLoopBackOff` | `kubectl -n mobi logs deploy/<service> --previous`. A schema validation error names the table or column; a missing migration or a stale database (see *Wipe all data*) is the usual cause. |
| `curl http://localhost/...` hangs or refuses | `kubectl -n envoy-gateway-system get pods` and `kubectl -n mobi get gateway mobi`. The Gateway should be `PROGRAMMED True`. |
| Web app: every page sends you to /login | The API isn't reachable from Vite. Check step 8, and that `VITE_API_TARGET` (if set) is right. |
| Login says *Too many sign-in attempts* | The edge allows 60 logins per minute per IP locally. Wait a minute. |
| Login says the account is locked | 5 wrong passwords lock it for 15 minutes. Wait, or unlock it as admin under **Users**. |
| Everyone got signed out after a redeploy | `JWT_PRIVATE_KEY` in `.env.local` changed or was removed. Keep it once generated. |
| Checkout returns 503 | A service order-service depends on is down, or order-service could not get its service token from auth-service. Check `kubectl -n mobi get pods` and order-service's logs. |
| No emails in Mailpit | Did you port-forward (section 12)? Then `kubectl -n mobi logs deploy/notification-service`. |
| Smoke test fails on the lockout step against a cloud cluster | Clouds allow only 10 logins a minute. Run with `SMOKE_SKIP_LOCKOUT=1`. |

Still stuck? `kubectl -n mobi get events --sort-by=.lastTimestamp | tail -30`
usually names the problem.

---

For how the pieces work, see [BACKEND.md](../BACKEND.md). For cloud deployments, see
[PORTABILITY.md](PORTABILITY.md) and [k8s/AWS_DEPLOYMENT_NOTES.md](../k8s/AWS_DEPLOYMENT_NOTES.md).

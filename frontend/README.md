# Povzetkar Frontend

Professional React/TypeScript frontend for the Povzetkar content orchestration platform.

## 🚀 Technology Stack

- **Framework:** React 19 + Vite 6
- **Language:** TypeScript (Strict Mode)
- **Styling:** Tailwind CSS 4 (Theme: Midnight Slate)
- **Routing:** TanStack Router (Type-safe, File-based)
- **Data Fetching:** TanStack Query (React Query) v5
- **API Client:** Axios + Orval (Automated TS generation from OpenAPI)
- **State Management:** Zustand
- **Notifications:** Sonner
- **Testing:** Vitest (Unit) + Playwright (E2E)

## 🛡️ Resilience & UX Features

### Global Error Handling
The application uses a dual-layer error handling strategy:
1. **React Error Boundary:** Catches unhandled rendering exceptions at the root.
2. **TanStack Router `defaultErrorComponent`:** Catches route-level loader or guard failures.
Both render the `ErrorPage` component found in `src/routes/ErrorPage.tsx`.

### Automatic Retries
Network stability is ensured via `axios-retry`. Requests are automatically retried up to 3 times with exponential backoff for:
- Network connectivity issues.
- Transient status codes: 502 (Bad Gateway), 503 (Service Unavailable), 504 (Gateway Timeout).

### Polished Loading States
A unified `Skeleton` component (`src/components/ui/Skeleton.tsx`) is used to provide consistent, glassmorphic pulse loaders across Dashboard widgets and data tables.

## 🛠️ Development

### Setup
```bash
npm install
```

### Run Local Dev
```bash
npm run dev
```

### Generate API Client
When the backend OpenAPI spec changes, regenerate the client:
```bash
npm run gen:api
```

### Testing
```bash
# Unit tests
npm test

# E2E tests
npx playwright test
```

## 📂 Project Structure

- `src/api`: Axios client configuration and generated Orval hooks.
- `src/components`: Reusable UI elements, categorized by domain (ui, layout, admin).
- `src/hooks`: Global React hooks (auth, events, toasts).
- `src/routes`: Page components and route definitions.
- `src/store`: Zustand stores for global state (auth, events).
- `src/util`: Helper utilities (logger, formatting).

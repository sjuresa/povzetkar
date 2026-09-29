import {createRootRouteWithContext, createRoute, createRouter, Outlet, redirect,} from '@tanstack/react-router';
import {LandingPage} from './routes/LandingPage';
import {ErrorPage} from './routes/ErrorPage';
import {AppShell} from './components/layout/AppShell';
import {RoutePending} from './components/layout/RoutePending';

// Define context for the router to enable type-safe auth guards
interface MyRouterContext {
  auth: {
    isAuthenticated: boolean;
    isLoaded: boolean;
    checkAuth: () => Promise<void>;
    user: import('./api/generated/model').UserDTO | null;
  };
}

// 1. Root Route - The foundation of the app
const rootRoute = createRootRouteWithContext<MyRouterContext>()({
  component: () => <Outlet />,
});

// 2. Public Index Route (Landing Page)
// Kept EAGER: this is the first-paint path for unauthenticated visitors (POV-202).
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
  beforeLoad: async ({ context }) => {
    // If we haven't checked auth yet, do it once
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }
  },
});

// 3. Protected Layout Route
// This route wraps all authenticated pages in the AppShell.
// Kept EAGER so its async auth `beforeLoad` guard runs BEFORE any lazy child chunk
// is requested (POV-202: prevents the lazy-behind-guard ordering hazard).
const protectedLayoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'authenticated',
  beforeLoad: async ({ context }) => {
    // If we haven't checked auth yet, do it once
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }

    // We must read the fresh state from zustand, not the React snapshot,
    // because checkAuth() just updated the store asynchronously.
    const isActuallyAuthenticated = (await import('./store/authStore')).useAuthStore.getState().isAuthenticated;

    // Final check - if still not authenticated, redirect to landing
    if (!isActuallyAuthenticated) {
      // eslint-disable-next-line @typescript-eslint/only-throw-error
      throw redirect({ to: '/' });
    }
  },
  component: () => (
    <AppShell>
      <Outlet />
    </AppShell>
  ),
});

// 4. Dashboard Route (Children of protected layout)
// Lazy-loaded (POV-202). `beforeLoad`/critical options stay here; only `component`
// is deferred into the `.lazy.tsx` wrapper.
const dashboardRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/dashboard',
}).lazy(() => import('./routes/DashboardPage.lazy').then((d) => d.Route));

const profileRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/me',
}).lazy(() => import('./routes/ProfilePage.lazy').then((d) => d.Route));

// Topics Route
const topicsRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/topics',
}).lazy(() => import('./pages/topics/TopicsPage.lazy').then((d) => d.Route));

// Briefing Library Route
const briefingsRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/briefings',
}).lazy(() => import('./routes/BriefingLibraryPage.lazy').then((d) => d.Route));

// Sources Routes
const sourcesRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/sources',
}).lazy(() => import('./routes/SourcesLibraryPage.lazy').then((d) => d.Route));

const sourceDetailRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/sources/$sourceId',
}).lazy(() => import('./routes/SourceDetailPage.lazy').then((d) => d.Route));

// 5. Briefing Detail Route
const briefingDetailRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/briefings/$briefingId',
}).lazy(() => import('./routes/BriefingDetailPage.lazy').then((d) => d.Route));

// 6. Admin Routes
// Layout kept EAGER (trivial `<Outlet/>` + permission `beforeLoad` guard); leaf
// components are lazy-loaded (POV-202).
const adminLayoutRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/admin',
  beforeLoad: async () => {
    const user = (await import('./store/authStore')).useAuthStore.getState().user;

    const hasAdminPerm = user?.permissions?.some(p =>
      ['ADMIN_AUDIT_LOG_VIEW', 'ADMIN_DASHBOARD_VIEW', 'USER_MGMT_VIEW'].includes(p)
    );

    if (!hasAdminPerm) {
      // eslint-disable-next-line @typescript-eslint/only-throw-error
      throw redirect({ to: '/dashboard' });
    }
  },
  component: () => <Outlet />,
});

const auditLogRoute = createRoute({
  getParentRoute: () => adminLayoutRoute,
  path: 'audit-log',
}).lazy(() => import('./routes/AuditLogPage.lazy').then((d) => d.Route));

const contentManagementRoute = createRoute({
  getParentRoute: () => adminLayoutRoute,
  path: 'content',
}).lazy(() => import('./routes/ContentManagementPage.lazy').then((d) => d.Route));

const adminJobsRoute = createRoute({
  getParentRoute: () => adminLayoutRoute,
  path: 'jobs',
}).lazy(() => import('./routes/AdminJobsPage.lazy').then((d) => d.Route));

const usersRoute = createRoute({
  getParentRoute: () => adminLayoutRoute,
  path: 'users',
}).lazy(() => import('./routes/UsersPage.lazy').then((d) => d.Route));

// 7. Public Sharing Route
// Lazy-loaded (POV-202): carries the markdown-heavy public briefing/summary views
// off the initial chunk. Reached only via a share link, never on first paint.
const publicShareRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/s/$uuid',
}).lazy(() => import('./routes/PublicResolverPage.lazy').then((d) => d.Route));

// 5. Create Route Tree
const routeTree = rootRoute.addChildren([
  indexRoute,
  publicShareRoute,
  protectedLayoutRoute.addChildren([
    dashboardRoute,
    profileRoute,
    topicsRoute,
    briefingsRoute,
    briefingDetailRoute,
    sourcesRoute,
    sourceDetailRoute,
    adminLayoutRoute.addChildren([
      auditLogRoute,
      contentManagementRoute,
      adminJobsRoute,
      usersRoute,
    ]),
  ]),
]);

// 6. Create Router Instance
export const router = createRouter({
  routeTree,
  defaultErrorComponent: ErrorPage,
  defaultPendingComponent: RoutePending,
  context: {
    auth: undefined!, // Injected at runtime via RouterProvider
  },
});

// 7. Register for Type Safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

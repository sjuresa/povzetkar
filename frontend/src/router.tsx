import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  Outlet,
  redirect,
} from '@tanstack/react-router';
import { LandingPage } from './routes/LandingPage';
import { DashboardPage } from './routes/DashboardPage';
import { BriefingDetailPage } from './routes/BriefingDetailPage';
import { AuditLogPage } from './routes/AuditLogPage';
import { AppShell } from './components/layout/AppShell';

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
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
  beforeLoad: async ({ context }) => {
    // If we haven't checked auth yet, do it once
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }
    
    // If definitely authenticated, skip landing and go to dashboard
    if (context.auth.isAuthenticated) {
      // eslint-disable-next-line @typescript-eslint/only-throw-error
      throw redirect({ to: '/dashboard' });
    }
  },
});

// 3. Protected Layout Route
// This route wraps all authenticated pages in the AppShell
const protectedLayoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'authenticated',
  beforeLoad: async ({ context }) => {
    // If we haven't checked auth yet, do it once
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }
    
    // Final check - if still not authenticated, redirect to landing
    if (!context.auth.isAuthenticated) {
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
const dashboardRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/dashboard',
  component: DashboardPage,
});

// Topics Placeholder
const topicsRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/topics',
  component: () => (
    <div className="flex flex-col items-center justify-center min-h-[50vh] text-slate-500 italic">
      Topics management coming soon
    </div>
  ),
});

// Briefings Placeholder
const briefingsRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/briefings',
  component: () => (
    <div className="flex flex-col items-center justify-center min-h-[50vh] text-slate-500 italic">
      All briefings list coming soon
    </div>
  ),
});

// 5. Briefing Detail Route
const briefingDetailRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/briefings/$briefingId',
  component: BriefingDetailPage,
});

// 6. Admin Routes
const adminLayoutRoute = createRoute({
  getParentRoute: () => protectedLayoutRoute,
  path: '/admin',
  beforeLoad: ({ context }) => {
    const hasAdminPerm = context.auth.user?.permissions?.some(p => 
      ['ADMIN_AUDIT_LOG_VIEW', 'ADMIN_DASHBOARD_VIEW'].includes(p)
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
  component: AuditLogPage,
});

const adminJobsRoute = createRoute({
  getParentRoute: () => adminLayoutRoute,
  path: 'jobs',
  component: () => <div className="p-8 text-slate-400 italic">Background Jobs Admin coming soon...</div>,
});

// 5. Create Route Tree
const routeTree = rootRoute.addChildren([
  indexRoute,
  protectedLayoutRoute.addChildren([
    dashboardRoute,
    topicsRoute,
    briefingsRoute,
    briefingDetailRoute,
    adminLayoutRoute.addChildren([
      auditLogRoute,
      adminJobsRoute
    ]),
  ]),
]);

// 6. Create Router Instance
export const router = createRouter({
  routeTree,
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

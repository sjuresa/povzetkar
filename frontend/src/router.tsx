import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  Outlet,
  redirect,
} from '@tanstack/react-router';
import { useAuthStore } from './store/authStore';
import { LandingPage } from './routes/LandingPage';
import { DashboardPage } from './routes/DashboardPage';
import { AppShell } from './components/layout/AppShell';

// Define context for the router to enable type-safe auth guards
interface MyRouterContext {
  auth: {
    isAuthenticated: boolean;
    isLoaded: boolean;
    checkAuth: () => Promise<void>;
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
    // If we're already logged in, skip landing and go to dashboard
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }
    if (useAuthStore.getState().isAuthenticated) {
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
    // Perform auth check if not already done
    if (!context.auth.isLoaded) {
      await context.auth.checkAuth();
    }
    
    // Final check - if still not authenticated, redirect to landing
    const isAuth = useAuthStore.getState().isAuthenticated;
    if (!isAuth) {
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

// 5. Create Route Tree
const routeTree = rootRoute.addChildren([
  indexRoute,
  protectedLayoutRoute.addChildren([dashboardRoute]),
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

import { useMemo } from 'react';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import { useAuthStore } from './store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { ErrorBoundary } from './components/error/ErrorBoundary';
import { ErrorPage } from './routes/ErrorPage';
import { ThemeProvider } from './components/theme/ThemeProvider';

function App() {
  // Only subscribe to the fields the router context needs for guards
  const auth = useAuthStore(
    useShallow((state) => ({
      isAuthenticated: state.isAuthenticated,
      isLoaded: state.isLoaded,
      checkAuth: state.checkAuth,
      user: state.user,
    }))
  );

  // Memoize the context object to prevent infinite re-render loops in RouterProvider
  const context = useMemo(() => ({ auth }), [auth]);

  return (
    <ErrorBoundary fallback={<ErrorPage />}>
      <ThemeProvider />
      <RouterProvider 
        router={router} 
        context={context} 
      />
    </ErrorBoundary>
  );
}

export default App;

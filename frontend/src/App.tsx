import { useMemo } from 'react';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import { useAuthStore } from './store/authStore';
import { useShallow } from 'zustand/react/shallow';

function App() {
  // Only subscribe to the fields the router context needs for guards
  const auth = useAuthStore(
    useShallow((state) => ({
      isAuthenticated: state.isAuthenticated,
      isLoaded: state.isLoaded,
      checkAuth: state.checkAuth,
    }))
  );

  // Memoize the context object to prevent infinite re-render loops in RouterProvider
  const context = useMemo(() => ({ auth }), [auth]);

  return (
    <RouterProvider 
      router={router} 
      context={context} 
    />
  );
}

export default App;

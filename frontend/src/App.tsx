import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import { useAuthStore } from './store/authStore';

function App() {
  const auth = useAuthStore();

  return (
    <RouterProvider 
      router={router} 
      context={{ auth }} 
    />
  );
}

export default App;

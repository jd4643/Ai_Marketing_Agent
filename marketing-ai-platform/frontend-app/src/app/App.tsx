import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { BusinessProvider } from '../hooks/use-business';
import { ToastProvider } from '../hooks/use-toast';
import { router } from './router';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BusinessProvider>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </BusinessProvider>
    </QueryClientProvider>
  );
}

import { createLazyRoute } from '@tanstack/react-router';
import { DashboardPage } from './DashboardPage';

export const Route = createLazyRoute('/authenticated/dashboard')({ component: DashboardPage });

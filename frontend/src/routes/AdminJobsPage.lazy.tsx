import { createLazyRoute } from '@tanstack/react-router';
import { AdminJobsPage } from './AdminJobsPage';

export const Route = createLazyRoute('/authenticated/admin/jobs')({ component: AdminJobsPage });

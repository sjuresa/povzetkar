import { createLazyRoute } from '@tanstack/react-router';
import { ContentManagementPage } from './ContentManagementPage';

export const Route = createLazyRoute('/authenticated/admin/content')({ component: ContentManagementPage });

import { createLazyRoute } from '@tanstack/react-router';
import { UsersPage } from './UsersPage';

export const Route = createLazyRoute('/authenticated/admin/users')({ component: UsersPage });

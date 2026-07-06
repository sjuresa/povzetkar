import { createLazyRoute } from '@tanstack/react-router';
import { ProfilePage } from './ProfilePage';

export const Route = createLazyRoute('/authenticated/me')({ component: ProfilePage });

import { createLazyRoute } from '@tanstack/react-router';
import { PublicResolverPage } from './PublicResolverPage';

export const Route = createLazyRoute('/s/$uuid')({ component: PublicResolverPage });

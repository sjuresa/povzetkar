import { createLazyRoute } from '@tanstack/react-router';
import { SourceDetailPage } from './SourceDetailPage';

export const Route = createLazyRoute('/authenticated/sources/$sourceId')({ component: SourceDetailPage });

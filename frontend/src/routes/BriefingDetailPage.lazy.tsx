import { createLazyRoute } from '@tanstack/react-router';
import { BriefingDetailPage } from './BriefingDetailPage';

export const Route = createLazyRoute('/authenticated/briefings/$briefingId')({ component: BriefingDetailPage });

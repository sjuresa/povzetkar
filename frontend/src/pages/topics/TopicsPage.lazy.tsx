import { createLazyRoute } from '@tanstack/react-router';
import { TopicsPage } from './TopicsPage';

export const Route = createLazyRoute('/authenticated/topics')({ component: TopicsPage });

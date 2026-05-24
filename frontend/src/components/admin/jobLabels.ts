import type { JobType } from '../../api/generated/model';

export const JOB_TYPE_LABELS: Record<JobType, string> = {
  SYNC_YOUTUBE_FEEDS: 'Sync Feeds',
  GENERATE_BRIEFINGS: 'Generate Briefings',
  PROCESS_TOPICS_AND_SUMMARIZE: 'Process & Summarize',
  GENERATE_TOPIC_BRIEFING: 'Topic Briefing',
  GENERATE_TOPIC_SUMMARIES: 'Topic Summaries',
  GENERATE_ITEM_SUMMARY: 'Item Summary',
  RETRY_ITEM_PROCESSING: 'Retry Item',
};

import { useQuery } from '@tanstack/react-query';
import client from '../api/client';
import type { SystemMetadataData } from '../components/system/SystemMetadata';

export const useSystemMetadata = (options?: { enabled?: boolean }) => {
  return useQuery<SystemMetadataData>({
    queryKey: ['system-metadata'],
    queryFn: async () => {
      const { data: envelope } = await client.get<{ data: SystemMetadataData }>('/api/v1/system/metadata');
      if (!envelope?.data) {
        throw new Error('System metadata not found');
      }
      return envelope.data;
    },
    staleTime: 1000 * 60 * 5, // 5 minutes
    ...options,
  });
};

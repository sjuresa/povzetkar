import { useQuery } from '@tanstack/react-query';
import client from '../api/client';
import type { SystemMetadataData } from '../components/system/SystemMetadata';

export const useSystemMetadata = (options?: { enabled?: boolean }) => {
  return useQuery<SystemMetadataData>({
    queryKey: ['system-metadata'],
    queryFn: async () => {
      const { data } = await client.get<SystemMetadataData>('/v1/system/metadata');
      return data;
    },
    staleTime: 1000 * 60 * 5, // 5 minutes
    ...options,
  });
};

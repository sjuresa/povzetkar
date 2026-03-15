import { defineConfig } from 'orval';

export default defineConfig({
  povzetkar: {
    input: {
      target: '../backend/target/openapi.json',
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      prettier: true,
      baseUrl: 'http://localhost:8080',
      override: {
        mutator: {
          path: './src/api/client.ts',
          name: 'customInstance',
        },
      },
    },
  },
});

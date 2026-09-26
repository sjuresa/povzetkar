import { defineConfig } from 'orval';

export default defineConfig({
  povzetkar: {
    input: {
      target: '../backend/target/openapi.json',
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated',
      clean: true,
      schemas: 'src/api/generated/model',
      client: 'react-query',
      baseUrl: '',
      prettier: false, // no prettier dependency: output must not depend on a global install (CI drift check)
      override: {
        mutator: {
          path: './src/api/client.ts',
          name: 'customInstance',
          alias: {
            SecondParameter: 'CustomRequestOptions',
            RequestOptions: 'CustomRequestOptions',
            RequestInit: 'CustomRequestOptions',
          },
        },
      },
    },
  },
});

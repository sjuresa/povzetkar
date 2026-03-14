import { useState } from 'react'

function App() {
  const [count, setCount] = useState(0)

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 text-neutral-50 p-8">
      <h1 className="text-6xl font-black tracking-tighter mb-4 animate-in fade-in slide-in-from-bottom-4 duration-1000">
        POVZETKAR
      </h1>
      <p className="text-neutral-400 mb-8 max-w-md text-center animate-in fade-in slide-in-from-bottom-8 duration-1000 delay-200">
        AI-Powered Briefings. 100% Type-Safe. Monorepo Foundations.
      </p>
      
      <button
        onClick={() => setCount((count) => count + 1)}
        className="px-6 py-3 bg-neutral-50 text-neutral-950 font-bold tracking-tight rounded-sm hover:scale-105 active:scale-95 transition-all animate-in fade-in slide-in-from-bottom-12 duration-1000 delay-400"
      >
        Count is {count}
      </button>
      
      <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-4 w-full max-w-4xl animate-in fade-in slide-in-from-bottom-16 duration-1000 delay-600">
        <div className="p-6 border border-neutral-800 bg-neutral-900/50">
          <h2 className="font-bold mb-2">React</h2>
          <p className="text-sm text-neutral-500">Modern UI library for interactive components.</p>
        </div>
        <div className="p-6 border border-neutral-800 bg-neutral-900/50">
          <h2 className="font-bold mb-2">Tailwind 4</h2>
          <p className="text-sm text-neutral-500">CSS-first configuration and performance.</p>
        </div>
        <div className="p-6 border border-neutral-800 bg-neutral-900/50">
          <h2 className="font-bold mb-2">Vite SSG</h2>
          <p className="text-sm text-neutral-500">Static pre-rendering for SEO and speed.</p>
        </div>
      </div>
    </div>
  )
}

export default App

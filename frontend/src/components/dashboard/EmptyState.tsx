import { Link } from '@tanstack/react-router';

export function EmptyState() {
  const steps = [
    { title: 'Connect a Source', description: 'Add a YouTube channel or RSS feed to start monitoring.', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
    { title: 'Create a Topic', description: 'Define what you want to track across your sources.', icon: 'M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01' },
    { title: 'Get Your Briefing', description: 'AI automatically extracts key points from new content.', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' },
  ];

  return (
    <div className="max-w-4xl mx-auto py-12 px-4 animate-in fade-in duration-1000">
      <div className="text-center mb-12">
        <h1 className="text-4xl font-extrabold tracking-tight text-white mb-4">
          Welcome to <span className="text-blue-500 text-glow">Povzetkar</span>
        </h1>
        <p className="text-xl text-slate-400">
          Your AI-powered content summarizer is ready. Let's set up your first briefing.
        </p>
      </div>

      <div className="grid md:grid-cols-3 gap-8 mb-12">
        {steps.map((step, idx) => (
          <div key={idx} className="glass p-8 rounded-3xl relative overflow-hidden group">
             <div className="text-blue-500 mb-6 bg-blue-600/10 w-14 h-14 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
               <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={step.icon} />
               </svg>
             </div>
             <h3 className="text-lg font-bold text-slate-100 mb-2">{step.title}</h3>
             <p className="text-slate-400 text-sm leading-relaxed">{step.description}</p>
             <div className="absolute top-0 right-0 w-24 h-24 bg-blue-600/5 blur-3xl -z-10"></div>
          </div>
        ))}
      </div>

      <div className="flex flex-col items-center space-y-6">
        <Link 
          to="/topics"
          className="px-8 py-4 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-2xl shadow-lg shadow-blue-900/40 transition-all hover:-translate-y-1 active:scale-95 text-lg"
        >
          Add Your First Topic
        </Link>
        <p className="text-sm text-slate-500">
          Need help? <a href="#" className="text-blue-400 hover:underline">Read the Quickstart Guide</a>
        </p>
      </div>
    </div>
  );
}

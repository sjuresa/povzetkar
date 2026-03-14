import { jsxs, jsx } from "react/jsx-runtime";
import { ViteReactSSG } from "vite-react-ssg";
import { useState } from "react";
function App() {
  const [count, setCount] = useState(0);
  return /* @__PURE__ */ jsxs("div", { className: "flex flex-col items-center justify-center min-h-screen bg-neutral-950 text-neutral-50 p-8", children: [
    /* @__PURE__ */ jsx("h1", { className: "text-6xl font-black tracking-tighter mb-4 animate-in fade-in slide-in-from-bottom-4 duration-1000", children: "POVZETKAR" }),
    /* @__PURE__ */ jsx("p", { className: "text-neutral-400 mb-8 max-w-md text-center animate-in fade-in slide-in-from-bottom-8 duration-1000 delay-200", children: "AI-Powered Briefings. 100% Type-Safe. Monorepo Foundations." }),
    /* @__PURE__ */ jsxs(
      "button",
      {
        onClick: () => setCount((count2) => count2 + 1),
        className: "px-6 py-3 bg-neutral-50 text-neutral-950 font-bold tracking-tight rounded-sm hover:scale-105 active:scale-95 transition-all animate-in fade-in slide-in-from-bottom-12 duration-1000 delay-400",
        children: [
          "Count is ",
          count
        ]
      }
    ),
    /* @__PURE__ */ jsxs("div", { className: "mt-16 grid grid-cols-1 md:grid-cols-3 gap-4 w-full max-w-4xl animate-in fade-in slide-in-from-bottom-16 duration-1000 delay-600", children: [
      /* @__PURE__ */ jsxs("div", { className: "p-6 border border-neutral-800 bg-neutral-900/50", children: [
        /* @__PURE__ */ jsx("h2", { className: "font-bold mb-2", children: "React" }),
        /* @__PURE__ */ jsx("p", { className: "text-sm text-neutral-500", children: "Modern UI library for interactive components." })
      ] }),
      /* @__PURE__ */ jsxs("div", { className: "p-6 border border-neutral-800 bg-neutral-900/50", children: [
        /* @__PURE__ */ jsx("h2", { className: "font-bold mb-2", children: "Tailwind 4" }),
        /* @__PURE__ */ jsx("p", { className: "text-sm text-neutral-500", children: "CSS-first configuration and performance." })
      ] }),
      /* @__PURE__ */ jsxs("div", { className: "p-6 border border-neutral-800 bg-neutral-900/50", children: [
        /* @__PURE__ */ jsx("h2", { className: "font-bold mb-2", children: "Vite SSG" }),
        /* @__PURE__ */ jsx("p", { className: "text-sm text-neutral-500", children: "Static pre-rendering for SEO and speed." })
      ] })
    ] })
  ] });
}
const routes = [
  {
    path: "/",
    element: /* @__PURE__ */ jsx(App, {})
  }
];
const createRoot = ViteReactSSG({ routes });
export {
  createRoot
};

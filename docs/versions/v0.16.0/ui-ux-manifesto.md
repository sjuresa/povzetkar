# UI/UX Manifesto: v0.16.0 (User Experience Focus)

**Goal:** Transform Povzetkar from a "Technical Summary Tool" into a "Premium Automation Partner".

---

## 1. Visual Identity & Accessibility

### 1.1 Color Palette (The Zinc Transition)
To exceed the 7:1 contrast ratio required for AAA compliance, we are moving from "Slate/Blue" towards a "Pure Zinc/Neutral" palette.

| Element | Neutral (Light) | Zinc (Dark) | hex Code |
|:---|:---|:---|:---|
| **Primary Background** | `Neutral-50` | `Zinc-950` | `#fafafa` / `#09090b` |
| **Secondary (Cards)** | `Neutral-100` | `Zinc-900` | `#f5f5f5` / `#18181b` |
| **Primary Text** | `Neutral-900` | `Zinc-50` | `#171717` / `#fafafa` |
| **Muted Text** | `Neutral-500` | `Zinc-400` | `#737373` / `#a1a1aa` |
| **Border/Dividers** | `Neutral-200` | `Zinc-800` | `#e5e5e5` / `#27272a` |

### 1.2 Typography (Premium Pairings)
- **Primary Body:** `Inter` (Sans-serif) - Balanced, readable, high x-height.
- **Display/Checklist:** `Outfit` (Geometric) - Added personality for onboarding and headings.
- **Headers:** `font-bold tracking-tight`.

---

## 2. Interactive Feedback

### 2.1 Micro-Animations & Sensory Cues
- **Checklist Entry:** `layout` transition (Springy: `stiffness: 300, damping: 30`) when an item is checked off.
- **Auditory Sync:** Toast trigger is paired with audio playback (<300ms delay) to reinforce the event.
- **Empty States:** "Empty Card" illustration (simple SVG) with a pulsating primary button to guide the user.

### 2.2 The "Aha! Moment" (Confetti Logic)
- **Trigger:** First briefing generation successfully complete + 100% Onboarding rate.
- **Effect:** `canvas-confetti` (School Pride style: Blue/Gold/White colors).
- **Justification:** Reinforce the value of the tool by celebrating the first successful automation.

---

## 3. UI Components API (Onboarding)

### 3.1 Checklist Component (ASCII Wireframe)
```text
  [ Checklist Container ]--------------------------------
  |  Title: "Getting Started"                           |
  |  Progress Bar: [ =======--- ] 70%                   |
  |-----------------------------------------------------|
  | [x] Create Topic     -> Link to /topics/new         |
  | [x] Add Source       -> Link to /sources/new        |
  | [ ] Trigger Briefing -> Action button (Play)        |
  | [ ] Read Summary     -> Link to /briefings          |
  | [ ] Share Summary    -> Action button (UUID Share)  |
  +-----------------------------------------------------+
```

---

## 4. Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.0     | 2026-04-01 | Povzetkar AI | Initialized visual guidelines for Version 0.16.0 (UX Focus).             |

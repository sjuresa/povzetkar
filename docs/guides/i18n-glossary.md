# UI Copy Glossary (SL / EN)

The terms and conventions for user-facing text in `frontend/public/locales/{sl,en}.json`. Introduced by POV-220,
which unified the Slovenian UI. The SL glossary term for a briefing is guarded by a test (`src/test/i18n.test.ts`).

## Terms

| Concept | SL | EN | Notes |
| :-- | :-- | :-- | :-- |
| Daily aggregate of a topic's summaries | **dnevni pregled** (short: pregled) | **daily briefing** (short: briefing) | One per topic and day — copy must not promise a single briefing across topics. Always qualified in SL labels ("Knjižnica pregledov", "Dnevni pregled za …") — bare "pregled" also means *inspection*. Never "poročilo" or "briefing" in SL. |
| Summary of a single video | **povzetek** | **summary** | Never used for a briefing. |
| Opening part of a briefing (summary across all its videos) | **bistvo dneva** | **executive summary** | "Povzetek za vodstvo" was replaced (PR #287 review, PO): it sounded unnatural and a briefing is more than that summary. |
| Group of sources with a prompt | **tema** | **topic** | |
| YouTube channel added to a topic | **vir** / YouTube kanal | **source** / YouTube channel | Only YouTube is accepted; do not promise RSS or blogs. |
| Briefing archive page | **Knjižnica pregledov** | **Briefing Library** | Same label in the sidebar, page title and onboarding. |

## Conventions

- **SL form of address: tikanje** ("Ustvari temo", "Tvoj račun čaka na odobritev"). Where a form needs a gender, the
  masculine generic is used ("Dobrodošel", "boš uporabljal").
- **Sentence case** in SL labels: only the first word is capitalised ("Nadzorna plošča", not
  "Nadzorna Plošča"). Exception: a page referred to by its navigation label keeps it as written ("Pojdi na Teme",
  "Odpri \"Knjižnica pregledov\"").
- **Instructions quote UI labels exactly** as they appear on the button or menu item (onboarding steps name
  "Zaženi orkestracijo", "Izvoz", "Knjižnica pregledov").
- **No claims the product cannot back:** features that are not implemented appear only as *planned* and only when
  `docs/overview/roadmap.md` lists them.
- **Out of scope for this glossary:** `system.changelog_entries` / `system.roadmap_phases` (generated from English
  docs by `sync_metadata.py`) and backend-generated titles.

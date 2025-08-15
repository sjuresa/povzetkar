# Projektna dokumentacija: Prihodnje naloge (TODO)

Ta dokument vsebuje seznam priporočenih dokumentov, ki jih bo koristno ustvariti v prihodnosti, ko bo projekt rasel in postajal kompleksnejši. Trenutna dokumentacija je zadostna za začetek, ti dokumenti pa bodo pomagali pri dolgoročnem vzdrževanju in vključevanju novih članov ekipe.

---

### 1. Zapisi o arhitekturnih odločitvah (Architecture Decision Records - ADRs)

*   **Namen:** Zabeležiti ključne arhitekturne odločitve, njihov kontekst in posledice. Odgovorijo na vprašanje *"Zakaj smo izbrali X namesto Y?"*.
*   **Primeri nalog:**
    *   Ustvariti ADR za izbiro ogrodja **Quarkus**.
    *   Ustvariti ADR za izbiro **PostgreSQL s `pgvector`** namesto specializirane vektorske baze.

### 2. Navodila za zagon in vzdrževanje (Deployment & Operations Guide / Runbook)

*   **Namen:** Podrobno opisati, kako aplikacijo zgraditi, zagnati, konfigurirati in nadzorovati v različnih okoljih.
*   **Primeri nalog:**
    *   Dokumentirati vse potrebne okoljske spremenljivke (npr. `DATABASE_URL`, `GEMINI_API_KEY`).
    *   Napisati navodila za zagon lokalnega okolja z `docker-compose`.
    *   Opisati postopek za migracijo podatkovne baze.

### 3. Vodič za nove razvijalce (Developer Onboarding Guide)

*   **Namen:** Omogočiti, da se nov član ekipe čim hitreje in samostojno vključi v delo.
*   **Primeri nalog:**
    *   Opisati postopek za kloniranje repozitorija in zagon celotnega sistema na lokalnem računalniku.
    *   Pojasniti, kako pognati teste in kje se nahajajo ključni deli kode.
    *   Definirati standarde kodiranja in potek dela z Gitom.
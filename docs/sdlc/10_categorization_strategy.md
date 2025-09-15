# Categorization Strategy

**Version:** 1.0
**Date:** September 11, 2025

## 1. The Added Value of Categorization

Categorization transforms the project from a simple summarization tool into an intelligent information analysis platform. Instead of a long, unstructured list of summaries, we get an organized and queryable knowledge base.

The key benefits are:

### 1.1. Precise Filtering and Searching
It allows users to instantly find all summaries related to a specific topic (e.g., all summaries with the category `large_language_model`) without relying on slow and imprecise full-text searches.

### 1.2. Enhanced Retrieval-Augmented Generation (RAG)
This is the most critical benefit for the project's conversational interface (v0.9). When a user asks a question, the system can first determine the question's category and then limit the search for relevant context **only to summaries within that category**. This dramatically improves both the **speed** (smaller search space) and **accuracy** (more relevant context) of the generated answers.

### 1.3. Automated Trend Analysis
The system can automatically detect emerging trends by analyzing the frequency of categories over time. A sudden spike in summaries categorized as `quantum_computing` can automatically trigger the creation of a new `Trend` entity.

### 1.4. Thematic Reporting
Beyond time-based briefings (e.g., daily), the system can generate thematic reports, such as a "Weekly Digest for the `AI_ethics` Category," providing deeper insights into specific areas of interest.

---

## 2. The Challenge of Consistency and Our Solution

A critical challenge with AI-driven categorization is the non-deterministic nature of Large Language Models (LLMs). If left unconstrained, an LLM might label similar content with different category names (e.g., `AI_ethics` vs. `ethics_of_AI`), creating a "tag soup" that would render all the benefits above useless.

To solve this, our strategy is based on **Constrained Choice, not Creation**.

### 2.1. The Strategy: Constrained Choice

The AI model will **not** be asked to invent or create category names. Instead, it will be forced to choose from a pre-defined, curated list of categories that are managed within our own system.

The workflow will be as follows:

1.  **Curated List:** A definitive list of all possible categories will be maintained in the `categories` table in our database. This list is the single source of truth.

2.  **Prompt Engineering:** When the `SummarizationService` calls the GenAI model to categorize a summary, the prompt will be specifically engineered to include this list.

    *   **Example Prompt Instruction:** *"Analyze the following text and choose the single most appropriate category from this list: [`AI_ETHICS`, `MODEL_ARCHITECTURE`, `QUANTUM_COMPUTING`, `ROBOTICS`]. Return only the chosen category name."*

3.  **Structured Output (Optional but Recommended):** To further increase reliability, we can instruct the model to return its choice in a structured format, such as JSON.

    *   **Example JSON Output:** `{"chosen_category": "AI_ETHICS"}`

4.  **Application Logic:** The application will receive the AI's response, find the corresponding `Category` entity in the database by its name, and create the link to the `Summary` entity.

### 2.2. Benefits of this Approach

*   **Guaranteed Consistency:** The system can only use categories that we have explicitly defined. There is zero chance of creating near-duplicate or inconsistently named categories.
*   **Simplicity and Robustness:** The application logic is simple and reliable. It involves a straightforward database lookup, not complex string matching or fuzzy logic.
*   **Control and Maintainability:** We, as the project maintainers, retain full control over the taxonomy of our knowledge base. We can add, merge, or retire categories over time in a structured and predictable way.

---

## 3. Future Evolution: A Hybrid "Suggest and Approve" Model

While the "Constrained Choice" strategy is essential for initial consistency, it can be too restrictive in the long term, failing to capture new, emerging concepts. To address this, a future version of the system can adopt a hybrid model.

### 3.1. The Hybrid Workflow

1.  **Dual-Task Prompting:** The AI model will be prompted to perform two tasks:
    *   **Task A (Mandatory):** Choose the best-fitting category from the existing, curated list.
    *   **Task B (Optional):** If no existing category is a good fit, suggest a new, more appropriate category name.

2.  **Storing Suggestions:** Suggested new categories will be stored separately (e.g., in a dedicated table or with a `SUGGESTED` status) and will not be used for querying or analysis until approved.

3.  **Human-in-the-Loop Review:** A user with administrative rights will have an interface to periodically review these suggestions. For each suggestion, they can:
    *   **Approve:** The suggestion becomes a new, official category.
    *   **Merge:** The suggestion is mapped as a synonym to an existing category.
    *   **Reject:** The suggestion is discarded.

### 3.2. Benefits of the Hybrid Model

*   **Adaptability:** The system can organically learn and adapt to new topics without sacrificing the consistency of the core taxonomy.
*   **Reduced Maintenance:** It reduces the manual effort of identifying missing categories by leveraging the AI to propose them.
*   **Maintained Control:** The final decision-making power over the taxonomy remains with a human, ensuring long-term quality and structure.
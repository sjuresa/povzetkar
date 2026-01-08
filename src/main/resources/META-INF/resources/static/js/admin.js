function buildOrchestrationUrl(form) {
    const formData = new FormData(form);
    const params = new URLSearchParams();
    const fields = ['startDate', 'endDate', 'processPending', 'summarize', 'createBriefing'];

    fields.forEach(field => {
        const value = formData.get(field);
        if (value) {
            params.append(field, value);
        }
    });

    const queryString = params.toString();
    return queryString ? `${form.action}?${queryString}` : form.action;
}

document.addEventListener('DOMContentLoaded', () => {

    async function handleAjaxFormSubmit(event, { successMessage, errorMessage, errorLogMessage }) {
        event.preventDefault();
        const form = event.target;
        let url = form.action;

        if (form.id === 'orchestrationForm') {
            url = buildOrchestrationUrl(form);
        }

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: '{}'
            });
            const responseBody = await response.text();
            if (response.ok) {
                displayFlashMessage(responseBody || successMessage, 'success');
            } else {
                displayFlashMessage(responseBody || errorMessage, 'danger');
            }
        } catch (error) {
            console.error(errorLogMessage, error);
            displayFlashMessage(`An unexpected error occurred: ${error.message}`, 'danger');
        }
    }

    const orchestrationForm = document.getElementById('orchestrationForm');
    if (orchestrationForm) {
        orchestrationForm.addEventListener('submit', (event) => handleAjaxFormSubmit(event, {
            successMessage: 'Orchestration process started successfully.',
            errorMessage: 'Failed to start orchestration process.',
            errorLogMessage: 'Error during orchestration AJAX call:'
        }));
    }

    const enrichSourcesForm = document.getElementById('enrichSourcesForm');
    if (enrichSourcesForm) {
        enrichSourcesForm.addEventListener('submit', (event) => handleAjaxFormSubmit(event, {
            successMessage: 'Content sources enrichment started successfully.',
            errorMessage: 'Failed to start content sources enrichment.',
            errorLogMessage: 'Error during enrich sources AJAX call:'
        }));
    }

    function displayFlashMessage(message, type) {
        const mainElement = document.querySelector('main.container');
        if (!mainElement) return;

        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type}`;
        alertDiv.setAttribute('role', 'alert');
        alertDiv.textContent = message;

        mainElement.prepend(alertDiv);

        setTimeout(() => {
            alertDiv.remove();
        }, 5000);
    }
});

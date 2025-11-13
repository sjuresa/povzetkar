document.addEventListener('DOMContentLoaded', () => {
    async function handleAjaxFormSubmit(event, { successMessage, errorMessage, errorLogMessage }) {
        event.preventDefault();
        const form = event.target;
        let url = form.action;

        // Special handling for orchestration form with query params
        if (form.id === 'orchestrationForm') {
            const formData = new FormData(form);
            const params = new URLSearchParams();
            const startDate = formData.get('startDate');
            const endDate = formData.get('endDate');
            if (startDate) params.append('startDate', startDate);
            if (endDate) params.append('endDate', endDate);
            if (params.toString()) {
                url += '?' + params.toString();
            }
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

        // Remove existing flash messages
        for (const el of document.querySelectorAll('.alert-success, .alert-danger')) {
            el.remove();
        }

        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type}`;
        alertDiv.setAttribute('role', 'alert');
        alertDiv.textContent = message;

        // Insert before the first child of main, or at the beginning of main
        mainElement.prepend(alertDiv);

        // Optional: Auto-remove message after a few seconds
        setTimeout(() => {
            alertDiv.remove();
        }, 5000);
    }
});

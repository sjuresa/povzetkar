document.addEventListener('DOMContentLoaded', () => {

    async function handleAjaxFormSubmit(event, { successMessage, errorMessage, errorLogMessage }) {
        event.preventDefault();
        const form = event.target;
        const formData = new FormData(form);
        const payload = Object.fromEntries(formData.entries());

        // Coerce checkbox values to booleans and add entries for unchecked boxes.
        form.querySelectorAll('input[type="checkbox"]').forEach(cb => {
            payload[cb.name] = payload.hasOwnProperty(cb.name);
        });

        // Remove empty string values so they are omitted from the JSON payload.
        for (const key in payload) {
            if (payload[key] === '') {
                delete payload[key];
            }
        }

        try {
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
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

    const fetchTranscriptButtons = document.querySelectorAll('.fetch-transcript-btn');
    fetchTranscriptButtons.forEach(button => {
        button.addEventListener('click', async function () {
            const contentItemId = this.dataset.id;
            const originalText = this.textContent;
            this.disabled = true;
            this.textContent = 'Fetching...';

            try {
                const response = await fetch(`/api/v1/admin/content-items/${contentItemId}/fetch-transcript`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    }
                });

                if (response.ok) {
                    const transcriptCell = document.querySelector(`#transcript-status-${contentItemId}`);
                    if (transcriptCell) {
                        transcriptCell.innerHTML = '<i class="fas fa-check-circle text-success" title="Transcript available"></i>';
                    }
                    this.textContent = 'Success!';
                    setTimeout(() => {
                        this.style.display = 'none'; // Hide button on success
                    }, 2000);
                } else {
                    const errorData = await response.json();
                    displayFlashMessage(`Error: ${errorData.message || 'Unknown error'}`, 'danger');
                    this.textContent = 'Failed';
                    setTimeout(() => {
                        this.disabled = false;
                        this.textContent = originalText;
                    }, 2000);
                }
            } catch (error) {
                console.error('Error fetching transcript:', error);
                displayFlashMessage('A network error occurred while fetching the transcript.', 'danger');
                this.disabled = false;
                this.textContent = originalText;
            }
        });
    });

    function displayFlashMessage(message, type) {
        const mainElement = document.querySelector('main.container');
        if (!mainElement) return;

        // Remove any existing flash messages
        const existingAlert = mainElement.querySelector('.alert');
        if (existingAlert) {
            existingAlert.remove();
        }

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

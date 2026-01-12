document.addEventListener('DOMContentLoaded', () => {
    const isMobile = globalThis.matchMedia("only screen and (max-width: 768px)").matches;

    if (isMobile) {
        const modal = document.getElementById('executive-summary-modal');
        const modalContent = document.getElementById('modal-executive-summary-content');
        const modalButton = document.getElementById('modal-view-full-summary-button');

        document.querySelectorAll('a[data-summary-url]').forEach(link => {
            link.addEventListener('click', (event) => {
                event.preventDefault();
                modalContent.textContent = link.dataset.executiveSummary;
                modalButton.href = link.dataset.summaryUrl;
                modal.showModal();
            });
        });
    }

    document.querySelectorAll('.fetch-transcript-btn').forEach(button => {
        button.addEventListener('click', async function() {
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

                if (!response.ok) {
                    throw new Error('Network response was not ok.');
                }

                await response.json();
                const transcriptCell = document.querySelector(`#transcript-status-${contentItemId}`);
                if (transcriptCell) {
                    transcriptCell.innerHTML = '<i class="fas fa-check-circle" style="color: green;" title="Transcript available"></i>';
                }
                this.textContent = 'Success';
                setTimeout(() => {
                    this.style.display = 'none';
                }, 2000);

            } catch (error) {
                console.error('Error fetching transcript:', error);
                this.textContent = 'Failed';
                setTimeout(() => {
                    this.disabled = false;
                    this.textContent = originalText;
                }, 2000);
            }
        });
    });
});

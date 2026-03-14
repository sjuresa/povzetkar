document.addEventListener('DOMContentLoaded', () => {
    const isMobile = () => window.innerWidth <= 768; // Example breakpoint for mobile

    const executiveSummaryModal = document.getElementById('executive-summary-modal');
    const modalContent = document.getElementById('modal-executive-summary-content');
    const modalViewFullSummaryButton = document.getElementById('modal-view-full-summary-button');

    // Event listener for the "View Full Summary" button inside the modal
    modalViewFullSummaryButton.addEventListener('click', (event) => {
        event.preventDefault();
        const targetUrl = modalViewFullSummaryButton.dataset.targetUrl;
        if (targetUrl) {
            window.open(targetUrl, '_blank');
            executiveSummaryModal.close(); // Close the modal after navigating
        }
    });

    document.querySelectorAll('#recent-content-items-with-summaries tbody a').forEach(link => {
        link.addEventListener('click', event => {
            if (isMobile()) {
                event.preventDefault(); // Prevent immediate navigation on mobile

                const executiveSummary = link.dataset.executiveSummary; // Get from data attribute
                const targetUrl = link.dataset.summaryUrl; // Get from data attribute

                modalContent.textContent = executiveSummary;
                modalViewFullSummaryButton.dataset.targetUrl = targetUrl; // Store URL for the button

                executiveSummaryModal.showModal(); // Show the Pico.css modal
            }
            // On desktop, default behavior (tooltip + new tab) is preserved
        });
    });
});

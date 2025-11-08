document.addEventListener('DOMContentLoaded', () => {
  for (const link of document.querySelectorAll('.confirm-submit')) {
    link.addEventListener('click', event => {
      event.preventDefault();
      const message = link.dataset.confirmMessage || 'Are you sure?';
      if (globalThis.confirm(message)) {
        link.closest('form').submit();
      }
    });
  }

  for (const link of document.querySelectorAll('.submit-form-on-click')) {
    link.addEventListener('click', event => {
      event.preventDefault();
      link.closest('form').submit();
    });
  }
});

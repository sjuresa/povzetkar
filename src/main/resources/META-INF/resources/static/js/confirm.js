function confirmAction(event, message) {
    if (!globalThis.confirm(message)) {
        event.preventDefault();
        return false;
    }
    return true;
}

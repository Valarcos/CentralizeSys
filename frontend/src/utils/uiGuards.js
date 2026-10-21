/**
 * Universal UI Guard: checks whether the user is actively interacting with any
 * modal dialog or if the page/document is currently hidden from view.
 * Standardizes background polling and auto-revalidation guards without per-modal state tracking.
 * @returns {boolean} true if any modal overlay is mounted or if the document is hidden.
 */
export const isUserInteracting = () => {
    if (typeof document === 'undefined') return false;
    const hasOpenModal = Boolean(document.querySelector('.modal-overlay'));
    return hasOpenModal || document.hidden;
};

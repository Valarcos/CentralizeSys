/**
 * Formats a number as ARS currency.
 * @param {number} value
 * @returns {string} Formatted currency string
 */
export const formatCurrency = (value) => {
    if (value === undefined || value === null) return '$0.00';
    return new Intl.NumberFormat('es-AR', {
        style: 'currency',
        currency: 'ARS',
        minimumFractionDigits: 2
    }).format(value);
};

/**
 * Formats a date string (YYYY-MM-DD) to a readable format (DD/MM/YYYY).
 * @param {string} dateString
 * @returns {string} Formatted date
 */
export const formatDate = (dateString) => {
    if (!dateString) return '';

    let dateStr = dateString;
    // If it's just a date string "YYYY-MM-DD", append time to force local parsing and avoid UTC offset issues
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        dateStr += 'T00:00:00';
    }
    // If it has a space instead of T (like some SQL formats), replace it for Safari/JS compatibility
    dateStr = dateStr.replace(' ', 'T');

    const date = new Date(dateStr);

    if (isNaN(date.getTime())) {
        // Fallback for completely unrecognized formats
        return dateString;
    }

    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();

    return `${hours}:${minutes}:${seconds} - ${day}/${month}/${year}`;
};

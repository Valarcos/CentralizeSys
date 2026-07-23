/**
 * Formats a number as ARS currency.
 * @param {number} value
 * @returns {string} Formatted currency string
 */
export const formatCurrency = (value) => {
    if (value === undefined || value === null) return '$ 0,00';
    const numStr = new Intl.NumberFormat('es-AR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(value);
    return `$ ${numStr}`;
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

/**
 * Formats a date string (YYYY-MM-DD) to a readable format (DD/MM/YYYY) without time.
 * @param {string} dateString
 * @returns {string} Formatted date without time
 */
export const formatDateOnly = (dateString) => {
    if (!dateString) return '';
    const dateStr = dateString.split('T')[0];
    const parts = dateStr.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateString;
};

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
    const date = new Date(dateString);
    // Adjust for timezone offset if necessary, or just parse string directly if YYYY-MM-DD
    // Simple split for YYYY-MM-DD to avoid timezone issues:
    if (dateString.includes('-')) {
        const [year, month, day] = dateString.split('-');
        return `${day}/${month}/${year}`;
    }
    return date.toLocaleDateString('es-AR');
};

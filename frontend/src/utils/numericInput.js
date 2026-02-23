/**
 * Numeric Input Utilities
 * Provides onKeyDown and onPaste handlers to restrict input to numbers only.
 * Two variants: Decimal (money/prices) and Integer (quantities).
 */

/**
 * onKeyDown for DECIMAL fields (money, prices, discounts).
 * Allows: digits (0-9), ONE decimal point, navigation keys.
 * Blocks: ALL letters, symbols, and shortcut injections.
 */
export const blockNonNumericKeys = (e) => {
    const allowedKeys = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight',
        'ArrowUp', 'ArrowDown', 'Home', 'End'];
    if (allowedKeys.includes(e.key)) return;
    if ((e.ctrlKey || e.metaKey) && ['a', 'c'].includes(e.key.toLowerCase())) return;
    if (/^[0-9.]$/.test(e.key)) return;
    e.preventDefault();
};

/**
 * onKeyDown for INTEGER fields (quantities — no decimal point allowed).
 */
export const blockNonIntegerKeys = (e) => {
    const allowedKeys = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight',
        'ArrowUp', 'ArrowDown', 'Home', 'End'];
    if (allowedKeys.includes(e.key)) return;
    if ((e.ctrlKey || e.metaKey) && ['a', 'c'].includes(e.key.toLowerCase())) return;
    if (/^[0-9]$/.test(e.key)) return;
    e.preventDefault();
};

/**
 * onPaste for DECIMAL fields: strips non-numeric except decimal point.
 */
export const sanitizeNumericPaste = (e) => {
    e.preventDefault();
    const cleaned = e.clipboardData.getData('text').replace(/[^0-9.]/g, '');
    document.execCommand('insertText', false, cleaned);
};

/**
 * onPaste for INTEGER fields: strips everything except digits.
 */
export const sanitizeIntegerPaste = (e) => {
    e.preventDefault();
    const cleaned = e.clipboardData.getData('text').replace(/[^0-9]/g, '');
    document.execCommand('insertText', false, cleaned);
};

/**
 * Enforce max 2 decimal places for MONEY inputs.
 * Strips non-numeric chars (except '.'), ensures single decimal point,
 * and limits to 2 digits after the decimal. No rounding — just truncates input.
 * Use this in onChange handlers for all money-related inputs.
 */
export const enforceMoneyFormat = (value) => {
    // Strip everything except digits and dots
    let cleaned = value.replace(/[^0-9.]/g, '');
    // Ensure only one decimal point
    const dotIndex = cleaned.indexOf('.');
    if (dotIndex !== -1) {
        cleaned = cleaned.substring(0, dotIndex + 1) + cleaned.substring(dotIndex + 1).replace(/\./g, '');
    }
    // Limit to 2 digits after decimal
    const parts = cleaned.split('.');
    if (parts.length === 2 && parts[1].length > 2) {
        cleaned = parts[0] + '.' + parts[1].substring(0, 2);
    }
    return cleaned;
};

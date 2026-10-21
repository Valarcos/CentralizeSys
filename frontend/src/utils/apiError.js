const NETWORK_ERROR_MESSAGE = 'No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.';

/**
 * Extracts a user-facing message from an Axios error.
 * @param {unknown} error
 * @param {string} fallback
 * @returns {string}
 */
export const getApiErrorMessage = (error, fallback = 'Error en la solicitud') => {
    if (!error?.response) return NETWORK_ERROR_MESSAGE;
    const data = error.response.data;
    if (data && typeof data === 'object' && typeof data.message === 'string' && data.message.trim()) {
        return data.message;
    }
    if (typeof data === 'string' && data.trim()) return data;
    return fallback;
};

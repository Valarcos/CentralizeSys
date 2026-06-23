import axios from 'axios';
import toast from 'react-hot-toast';

const API_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
    timeout: 10000,
});

// Request Interceptor: Inject JWT from localStorage
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('jwt');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        console.error('Request interceptor error:', error);
        return Promise.reject(error);
    }
);

// Response Interceptor: Handle errors with toasts
api.interceptors.response.use(
    (response) => response,
    (error) => {
        const message = error.response?.data?.message
            || error.response?.data
            || error.message
            || 'Error en la solicitud';

        toast.error(message);

        // Auto-logout on 401 Unauthorized
        if (error.response?.status === 401) {
            localStorage.removeItem('jwt');
            localStorage.removeItem('userEmail');
            localStorage.removeItem('userName');

            // Persist the error message so it survives the hard page reload.
            // LoginPage reads and clears this on mount to show a toast to the user.
            sessionStorage.setItem('pendingAuthError', String(message));

            // Guard: only redirect if NOT already on /login to prevent a silent
            // self-reload loop (which was destroying the toast and wiping the form).
            if (window.location.pathname !== '/login') {
                console.error('[api.js] 401 received — redirecting to login. URI that triggered it:', error.config?.url);
                window.location.href = '/login';
            }
        }

        return Promise.reject(error);
    }
);

export default api;

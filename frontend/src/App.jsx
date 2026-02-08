import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import AdminPage from './pages/AdminPage';
import InventarioPage from './pages/InventarioPage';
import AppLayout from './layouts/AppLayout';

/**
 * Protects routes from unauthenticated access.
 * Redirects to login if no JWT token is present.
 */
function ProtectedRoute({ children }) {
    const token = localStorage.getItem('jwt');

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    return children;
}

/**
 * Protects admin-only routes.
 * Redirects to dashboard if user is not ADMIN.
 */
function AdminRoute({ children }) {
    const role = localStorage.getItem('userRole');
    return role === 'ADMIN' ? children : <Navigate to="/dashboard" replace />;
}

/**
 * Placeholder component for pages not yet implemented.
 */
function PlaceholderPage({ title, sprint = 3 }) {
    return (
        <div className="container">
            <h1>{title}</h1>
            <p>Próximamente en Sprint {sprint}</p>
        </div>
    );
}

function App() {
    return (
        <BrowserRouter>
            <Toaster
                position="top-center"
                toastOptions={{
                    duration: 4000,
                    style: {
                        fontSize: '1.1rem',
                        padding: '16px',
                        maxWidth: '500px',
                    },
                    success: {
                        iconTheme: {
                            primary: 'var(--color-success)',
                            secondary: 'white',
                        },
                    },
                    error: {
                        duration: 8000,
                        iconTheme: {
                            primary: 'var(--color-error)',
                            secondary: 'white',
                        },
                    },
                }}
            />

            <Routes>
                <Route path="/login" element={<LoginPage />} />

                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <AppLayout />
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<Navigate to="/dashboard" replace />} />
                    <Route path="dashboard" element={<DashboardPage />} />
                    <Route path="ventas" element={<PlaceholderPage title="Ventas" sprint={4} />} />
                    <Route path="inventario" element={<InventarioPage />} />
                    <Route path="deudores" element={<PlaceholderPage title="Deudores" sprint={4} />} />
                    <Route path="reportes" element={<PlaceholderPage title="Reportes" sprint={5} />} />
                    <Route
                        path="admin"
                        element={
                            <AdminRoute>
                                <AdminPage />
                            </AdminRoute>
                        }
                    />
                </Route>

                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;

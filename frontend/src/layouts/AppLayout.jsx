import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect } from 'react';
import LogoutModal from '../components/LogoutModal';
import './AppLayout.css';

export default function AppLayout() {
    const [showLogoutModal, setShowLogoutModal] = useState(false);
    const [userName, setUserName] = useState('');
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        const name = localStorage.getItem('userName') || 'Usuario';
        setUserName(name);
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('jwt');
        localStorage.removeItem('userEmail');
        localStorage.removeItem('userName');
        navigate('/login');
    };

    const isActive = (path) => location.pathname.startsWith(path);

    return (
        <div className="app-layout">
            {/* Desktop Top Navigation */}
            <nav className="top-nav desktop-only" aria-label="Navegación principal">
                <div className="container nav-content">
                    <h1 className="brand">CentralizeSys</h1>
                    <div className="nav-user-section">
                        <span className="user-greeting">Hola, {userName}</span>
                        <button
                            onClick={() => setShowLogoutModal(true)}
                            className="logout-btn"
                            aria-label="Cerrar sesión"
                        >
                            Salir
                        </button>
                    </div>
                </div>
            </nav>

            {/* Mobile Compact Top Bar */}
            <nav className="top-nav-mobile mobile-only" aria-label="Navegación">
                <div className="mobile-nav-content">
                    <h1 className="brand-mobile">CentralizeSys</h1>
                    <button
                        onClick={() => setShowLogoutModal(true)}
                        className="logout-btn-mobile"
                        aria-label="Cerrar sesión"
                    >
                        Salir
                    </button>
                </div>
            </nav>

            {/* Main Content */}
            <main className="main-content">
                <Outlet />
            </main>

            {/* Mobile Bottom Navigation */}
            <nav className="bottom-nav mobile-only" aria-label="Navegación móvil">
                <button
                    onClick={() => navigate('/dashboard')}
                    className={isActive('/dashboard') ? 'active' : ''}
                    aria-label="Ir a inicio"
                    aria-current={isActive('/dashboard') ? 'page' : undefined}
                >
                    <span className="icon" aria-hidden="true">🏠</span>
                    <span>Inicio</span>
                </button>

                <button
                    onClick={() => navigate('/pos')}
                    className={isActive('/pos') ? 'active' : ''}
                    aria-label="Ir a punto de venta"
                    aria-current={isActive('/pos') ? 'page' : undefined}
                >
                    <span className="icon" aria-hidden="true">💰</span>
                    <span>POS</span>
                </button>

                <button
                    onClick={() => navigate('/stock')}
                    className={isActive('/stock') ? 'active' : ''}
                    aria-label="Ir a inventario"
                    aria-current={isActive('/stock') ? 'page' : undefined}
                >
                    <span className="icon" aria-hidden="true">📦</span>
                    <span>Stock</span>
                </button>

                <button
                    onClick={() => navigate('/account')}
                    className={isActive('/account') ? 'active' : ''}
                    aria-label="Ir a cuenta"
                    aria-current={isActive('/account') ? 'page' : undefined}
                >
                    <span className="icon" aria-hidden="true">👤</span>
                    <span>Cuenta</span>
                </button>
            </nav>

            <LogoutModal
                isOpen={showLogoutModal}
                onConfirm={handleLogout}
                onCancel={() => setShowLogoutModal(false)}
            />
        </div>
    );
}

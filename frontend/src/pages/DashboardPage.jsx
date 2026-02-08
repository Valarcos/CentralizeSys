import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import MorningAlert from '../components/MorningAlert';
import './DashboardPage.css';

export default function DashboardPage() {
    const [userName, setUserName] = useState('');
    const [userRole, setUserRole] = useState('');
    const [lowStockProducts, setLowStockProducts] = useState([]);
    const [showMorningAlert, setShowMorningAlert] = useState(false);
    const [debtorReminder, setDebtorReminder] = useState(null);
    const navigate = useNavigate();

    useEffect(() => {
        setUserName(localStorage.getItem('userName') || 'Usuario');
        setUserRole(localStorage.getItem('userRole') || 'EMPLEADO');

        // Fetch low stock alerts
        fetchLowStockAlerts();
        // Fetch debtor reminder
        fetchDebtorReminder();
    }, []);

    const fetchLowStockAlerts = async () => {
        try {
            const response = await api.get('/api/productos/alerts');
            if (response.data && response.data.length > 0) {
                setLowStockProducts(response.data);
                setShowMorningAlert(true);
            }
        } catch (error) {
            // Silently fail - alerts are optional, endpoint may not exist yet
            console.log('Low stock alerts not available');
        }
    };

    const fetchDebtorReminder = async () => {
        try {
            const response = await api.get('/api/deudores/reminder');
            if (response.data) {
                setDebtorReminder(response.data);
            }
        } catch (error) {
            // Silently fail - reminder is optional, endpoint may not exist yet
            console.log('Debtor reminder not available');
        }
    };

    return (
        <div className="dashboard container">
            <h1>Bienvenido, {userName}</h1>
            <p className="dashboard-subtitle">Sistema operativo y listo</p>

            {/* Debtor Reminder Badge */}
            {debtorReminder && debtorReminder.count > 0 && (
                <div
                    className="debtor-reminder"
                    role="alert"
                    aria-label="Recordatorio de deudores"
                    onClick={() => navigate('/deudores')}
                >
                    <span className="reminder-icon" aria-hidden="true">💳</span>
                    <span className="reminder-text">
                        Tiene <strong>{debtorReminder.count}</strong> deudores con pagos pendientes
                    </span>
                    <span className="reminder-amount">
                        Total: <strong>${debtorReminder.totalAmount?.toLocaleString('es-AR') || 0}</strong>
                    </span>
                    <span className="reminder-arrow" aria-hidden="true">→</span>
                </div>
            )}

            <div className="dashboard-grid">
                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/ventas')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/ventas')}
                >
                    <h3>💰 Punto de Venta</h3>
                    <p>Registrar ventas y cobros</p>
                </div>

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/inventario')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/inventario')}
                >
                    <h3>📦 Gestión de Inventario</h3>
                    <p>Administrar productos y stock</p>
                </div>

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/deudores')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/deudores')}
                >
                    <h3>👥 Deudores</h3>
                    <p>Gestionar cuentas por cobrar</p>
                </div>

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/reportes')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/reportes')}
                >
                    <h3>📊 Reportes</h3>
                    <p>Ver estadísticas y análisis</p>
                </div>

                {userRole === 'ADMIN' && (
                    <div
                        className="dashboard-card clickable admin-card"
                        onClick={() => navigate('/admin')}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => e.key === 'Enter' && navigate('/admin')}
                    >
                        <h3>🔐 Administración</h3>
                        <p>Gestionar usuarios del sistema</p>
                    </div>
                )}
            </div>

            {/* Morning Alert Modal */}
            {showMorningAlert && (
                <MorningAlert
                    lowStockProducts={lowStockProducts}
                    onDismiss={() => setShowMorningAlert(false)}
                />
            )}
        </div>
    );
}

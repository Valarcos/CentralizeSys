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
    // const [debtorReminder, setDebtorReminder] = useState(null); // Future Sprint
    const [backupWarning, setBackupWarning] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        setUserName(localStorage.getItem('userName') || 'Usuario');
        setUserRole(localStorage.getItem('userRole') || 'EMPLEADO');

        const fetchDashboardData = async () => {
            try {
                // calls to /api/deudores/* are commented out as they belong to Sprint 5/6
                const [stockRes, backupRes] = await Promise.all([
                    api.get('/api/productos/alerts'),
                    // api.get('/api/deudores/expired').catch(() => ({ data: [] })),
                    api.get('/api/backups/last').catch(() => ({ data: null })),
                    // api.get('/api/deudores/reminder').catch(() => ({ data: null }))
                ]);

                // Low Stock Alerts
                if (stockRes.data && stockRes.data.length > 0) {
                    setLowStockProducts(stockRes.data);
                    setShowMorningAlert(true);
                }

                // Debtor Logic (Future)
                /*
                if (reminderRes.data) {
                    setDebtorReminder(reminderRes.data);
                }
                setExpiredDebtors(debtorsRes.data);
                */

                // Check Backup Status
                if (!backupRes.data) {
                    setBackupWarning(true); // Never backed up or endpoint 404
                } else {
                    const lastDate = new Date(backupRes.data.date || backupRes.data);
                    const now = new Date();
                    const hoursDiff = (now - lastDate) / (1000 * 60 * 60);
                    if (hoursDiff > 24) {
                        setBackupWarning(true);
                    }
                }

            } catch (error) {
                console.error('Error loading dashboard data:', error);
            }
        };

        fetchDashboardData();
    }, []);

    return (
        <div className="dashboard container">
            <h1>Bienvenido, {userName}</h1>
            <p className="dashboard-subtitle">Sistema operativo y listo</p>

            {/* Backup Warning */}
            {backupWarning && (
                <div className="alert-card warning" onClick={() => navigate('/backups')}>
                    <span className="alert-icon">⚠️</span>
                    <div className="alert-content">
                        <strong>Respaldo Requerido</strong>
                        <p>No se ha realizado una copia de seguridad en las últimas 24 horas.</p>
                    </div>
                    <span className="alert-action">Realizar ahora →</span>
                </div>
            )}

            {/* Debtor Reminder Badge (Future Sprint) */}
            {/*
                debtorReminder && debtorReminder.count > 0 && (
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
                )
            */}

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

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/backups')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/backups')}
                >
                    <h3>💾 Respaldos</h3>
                    <p>Copias de seguridad y restauración</p>
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
                        <p>Gestionar usuarios y permisos</p>
                    </div>
                )}
            </div>

            {/* Morning Alert Modal */}
            {
                showMorningAlert && (
                    <MorningAlert
                        lowStockProducts={lowStockProducts}
                        onDismiss={() => setShowMorningAlert(false)}
                    />
                )
            }
        </div >
    );
}

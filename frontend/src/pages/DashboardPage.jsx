import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import StockWarningModal from '../components/StockWarningModal';
import './DashboardPage.css';

export default function DashboardPage() {
    const [userName, setUserName] = useState('');
    const [userRole, setUserRole] = useState('');
    const [lowStockProducts, setLowStockProducts] = useState([]);
    const [showMorningAlert, setShowMorningAlert] = useState(false);
    const [hasActiveDebts, setHasActiveDebts] = useState(false);
    const [backupWarning, setBackupWarning] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        setUserName(localStorage.getItem('userName') || 'Usuario');
        setUserRole(localStorage.getItem('userRole') || 'EMPLEADO');

        const fetchDashboardData = async () => {
            try {
                const [stockRes, backupRes, reminderRes] = await Promise.all([
                    api.get('/api/productos/alerts'),
                    api.get('/api/backups/last').catch(() => ({ data: null })),
                    api.get('/api/deudores/reminder').catch(() => ({ data: false }))
                ]);

                // Low Stock Alerts
                if (stockRes.data && stockRes.data.length > 0) {
                    setLowStockProducts(stockRes.data);
                    setShowMorningAlert(true);
                }

                // Debtor Logic
                if (reminderRes.data === true) {
                    setHasActiveDebts(true);
                }

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

        // Initial Load
        fetchDashboardData();

        // Smart Timer: Check clock every minute, but only trigger API at specific hours
        const targetHours = [8, 10, 12, 14, 16, 18, 20];
        let lastCheckedHour = new Date().getHours();

        const checkTime = () => {
            const now = new Date();
            const currentHour = now.getHours();

            // Logic:
            // 1. Is it a target hour?
            // 2. Have we already checked this hour? (Prevent infinite loops in that hour)
            if (targetHours.includes(currentHour) && currentHour !== lastCheckedHour) {
                console.log(`⏰ Triggering Scheduled Stock Check at ${currentHour}:00`);
                fetchDashboardData();
                lastCheckedHour = currentHour;
            }
            // Reset tracker if hour changes to a non-target hour (so next target hits)
            else if (currentHour !== lastCheckedHour) {
                lastCheckedHour = currentHour;
            }
        };

        // Check every 60 seconds to catch the hour change
        const intervalId = setInterval(checkTime, 60 * 1000);

        return () => clearInterval(intervalId);
    }, []);

    return (
        <div className="dashboard container">
            <h1>Bienvenido, {userName}</h1>
            <p className="dashboard-subtitle">Sistema operativo y listo</p>

            {/* Debtor Reminder Badge */}
            {hasActiveDebts && (
                <div
                    className="alert-card info clickable"
                    onClick={() => navigate('/deudores')}
                >
                    <span className="alert-icon">💳</span>
                    <div className="alert-content">
                        <strong>Cuentas por Cobrar Activas</strong>
                        <p>Existen cuentas pendientes de cobro.</p>
                    </div>
                    <span className="alert-action">Ver Deudores →</span>
                </div>
            )}

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
                    <>


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
                    </>
                )}
            </div>

            {/* Morning Alert Modal */}
            {
                showMorningAlert && (
                    <StockWarningModal
                        affectedProducts={lowStockProducts}
                        onClose={() => setShowMorningAlert(false)}
                        onContinue={() => setShowMorningAlert(false)}
                    />
                )
            }
        </div >
    );
}

import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import NegativeStockCorrectionModal from '../components/NegativeStockCorrectionModal';
import { APP_REVALIDATE_EVENT } from '../utils/appEvents';
import { isUserInteracting } from '../utils/uiGuards';
import './DashboardPage.css';

export default function DashboardPage() {
    const [userName, setUserName] = useState('');
    const [userRole, setUserRole] = useState('');
    const [negativeStockProducts, setNegativeStockProducts] = useState([]);
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [hasActiveDebts, setHasActiveDebts] = useState(false);
    const [pendingCheques, setPendingCheques] = useState([]);
    const [backupWarning, setBackupWarning] = useState(false);
    const [systemAlerts, setSystemAlerts] = useState([]);
    const isMounted = useRef(true);
    const isFetchingRef = useRef(false);
    const navigate = useNavigate();

    const refreshNegativeStock = async () => {
        try {
            const stockRes = await api.get('/productos/alerts');
            const data = stockRes.data && stockRes.data.length > 0 ? stockRes.data : [];
            if (!isMounted.current) return;
            setNegativeStockProducts(data);
            if (data.length === 0) {
                setShowCorrectionModal(false);
            }
        } catch {
            // Error managed by global api interceptor
        }
    };

    useEffect(() => {
        isMounted.current = true;
        setUserName(localStorage.getItem('userName') || 'Usuario');
        setUserRole(localStorage.getItem('userRole') || 'EMPLEADO');

        const fetchDashboardData = async ({ silent = false } = {}) => {
            if (isFetchingRef.current) return;
            isFetchingRef.current = true;
            try {
                const response = await api.get('/dashboard/summary', silent ? { silent: true } : undefined);
                if (!isMounted.current || !response.data) return;

                const { stockAlerts, lastBackup, hasActiveDebts, pendingCheques, systemAlerts } = response.data;

                // Negative Stock Alerts — banner shows whenever there are negative products
                setNegativeStockProducts(Array.isArray(stockAlerts) ? stockAlerts : []);

                // Debtor Logic
                setHasActiveDebts(Boolean(hasActiveDebts));

                // Cheques Logic
                setPendingCheques(Array.isArray(pendingCheques) ? pendingCheques : []);

                // Check Backup Status
                if (!lastBackup) {
                    setBackupWarning(true); // Never backed up
                } else {
                    const lastDate = new Date(lastBackup.date || lastBackup);
                    const now = new Date();
                    const hoursDiff = (now - lastDate) / (1000 * 60 * 60);
                    setBackupWarning(hoursDiff > 24);
                }

                // System Alerts (e.g., restore failures)
                if (Array.isArray(systemAlerts) && systemAlerts.length > 0) {
                    setSystemAlerts(systemAlerts);
                } else {
                    setSystemAlerts([]);
                }
            } catch {
                // Intercepted globally; silent mode suppresses toasts during automatic cycles
            } finally {
                isFetchingRef.current = false;
            }
        };

        // Initial Load
        fetchDashboardData({ silent: false });

        // Smart Polling (Decisión D-4): limitado exclusivamente a Dashboard (120s) y CobrosYPedidos (90s)
        // Intervalo pausado automáticamente si cualquier modal está montado en el DOM (.modal-overlay) o la pestaña está oculta
        const intervalId = setInterval(() => {
            if (!isUserInteracting() && isMounted.current) {
                fetchDashboardData({ silent: true });
            }
        }, 120 * 1000);

        // Revalidación inmediata al restaurar la app tras inactividad (emitido por AppLayout)
        const handleRevalidate = () => {
            if (!isUserInteracting() && isMounted.current) {
                fetchDashboardData({ silent: true });
            }
        };
        window.addEventListener(APP_REVALIDATE_EVENT, handleRevalidate);

        return () => {
            clearInterval(intervalId);
            window.removeEventListener(APP_REVALIDATE_EVENT, handleRevalidate);
            isMounted.current = false;
        };
    }, []);

    return (
        <div className="dashboard container">
            <h1>Bienvenido, {userName}</h1>
            <p className="dashboard-subtitle">Sistema operativo y listo</p>

            {/* System Critical Alerts (e.g., failed DB restoration) */}
            {(systemAlerts || []).map((alert, idx) => (
                <div key={idx} className="alert-card error">
                    <span className="alert-icon">🚨</span>
                    <div className="alert-content">
                        <strong>Alerta del Sistema</strong>
                        <p>{alert.message}</p>
                    </div>
                    <button
                        className="alert-close-btn"
                        onClick={() => setSystemAlerts(prev => prev.filter((_, i) => i !== idx))}
                        aria-label="Cerrar alerta"
                    >
                        ✕
                    </button>
                </div>
            ))}

            {/* Negative Stock Inline Banner — persists until all negative stock is corrected */}
            {negativeStockProducts.length > 0 && (
                <div className="alert-card danger clickable" onClick={() => setShowCorrectionModal(true)}>
                    <span className="alert-icon">🔴</span>
                    <div className="alert-content">
                        <strong>Stock Negativo Detectado</strong>
                        <p>
                            {negativeStockProducts.length === 1
                                ? `1 producto tiene stock negativo.`
                                : `${negativeStockProducts.length} productos tienen stock negativo.`}
                        </p>
                    </div>
                    <span className="alert-action">Corregir Inventario →</span>
                </div>
            )}

            {/* Debtor Reminder Badge */}
            {hasActiveDebts && (
                <div
                    className="alert-card info clickable"
                    onClick={() => navigate('/cobros-y-pedidos')}
                >
                    <span className="alert-icon">💳</span>
                    <div className="alert-content">
                        <strong>Cobros y Pedidos Pendientes</strong>
                        <p>Existen cuentas por cobrar o ventas pendientes de pago.</p>
                    </div>
                    <span className="alert-action">Ver Pendientes →</span>
                </div>
            )}

            {/* Cheques Reminder Badge */}
            {pendingCheques.length > 0 && (
                <div
                    className="alert-card clickable"
                    onClick={() => navigate('/cobros-y-pedidos', { state: { filter: 'CHEQUE' } })}
                    style={{ backgroundColor: '#ccfbf1', borderLeft: '4px solid #0d9488' }}
                >
                    <span className="alert-icon" style={{color: '#0d9488'}}>⚠️</span>
                    <div className="alert-content" style={{color: '#0d9488'}}>
                        <strong style={{color: '#0d9488'}}>Cheques Pendientes</strong>
                        <p>Tienes {pendingCheques.length} {pendingCheques.length === 1 ? 'cheque' : 'cheques'} pendiente(s) para cobro hoy o atrasado(s).</p>
                    </div>
                    <span className="alert-action" style={{color: '#0d9488'}}>Ir a Cobros →</span>
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
                    onClick={() => navigate('/cobros-y-pedidos')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/cobros-y-pedidos')}
                >
                    <h3>💳 Pendientes</h3>
                    <p>Gestionar deudas y ventas pendientes de pago</p>
                </div>

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/reportes')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/reportes')}
                >
                    <h3>📊 Reportes</h3>
                    <p>Ver estadísticas y análisis financiero</p>
                </div>

                <div
                    className="dashboard-card clickable"
                    onClick={() => navigate('/historial-ventas')}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && navigate('/historial-ventas')}
                >
                    <h3>📖 Historial</h3>
                    <p>Consultar el historial de ventas registradas</p>
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

            {/* Negative Stock Correction Modal (Issue #3.1) */}
            {showCorrectionModal && (
                <NegativeStockCorrectionModal
                    products={negativeStockProducts}
                    onClose={() => setShowCorrectionModal(false)}
                    onCorrectionComplete={refreshNegativeStock}
                />
            )}
        </div>
    );
}

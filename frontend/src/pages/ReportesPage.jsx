import { useState, useEffect } from 'react';
import api from '../services/api';
import { formatCurrency } from '../utils/format';
import toast from 'react-hot-toast';
import './ReportesPage.css';

export default function ReportesPage() {
    const [granularity, setGranularity] = useState('MONTH'); // 'DAY' | 'MONTH' | 'YEAR'
    const [date, setDate] = useState(() => new Date().toISOString().split('T')[0]);
    const [year, setYear] = useState(() => new Date().getFullYear());
    const [month, setMonth] = useState(() => new Date().getMonth() + 1);

    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);

    const loadStats = async () => {
        setLoading(true);
        try {
            const params = {};
            if (granularity === 'YEAR') {
                params.year = year;
            } else if (granularity === 'MONTH') {
                params.year = year;
                params.month = month;
            } else if (granularity === 'DAY') {
                const [y, m, d] = date.split('-');
                params.year = y;
                params.month = parseInt(m, 10);
                params.day = parseInt(d, 10);
            }

            const res = await api.get('/reportes/estadisticas', { params });
            setStats(res.data);
        } catch (error) {
            console.error("Error loading stats:", error);
            toast.error("Error al cargar las estadísticas financieras");
        } finally {
            setLoading(false);
        }
    };

    // Auto-refresh when parameters change
    useEffect(() => {
        loadStats();
    }, [granularity, date, year, month]);

    const rc = stats?.rendimientoComercial;
    const fc = stats?.flujoDeCaja;

    // Derived Financial Metrics
    const gananciaBruta = rc ? rc.ingresosVentas - rc.costoTotalVendido : 0;
    const margenPct = rc && rc.ingresosVentas > 0 ? (gananciaBruta / rc.ingresosVentas) * 100 : 0;

    return (
        <div className="reportes-page">
            <div className="reportes-header">
                <h1>Reportes Financieros</h1>
            </div>

            {/* Instruction 3: Controls row — toggles + date pickers share the same horizontal container */}
            <div className="reportes-controls-row">
                <div className="granularity-toggles">
                    <button
                        className={`toggle-btn-massive ${granularity === 'DAY' ? 'active' : ''}`}
                        onClick={() => setGranularity('DAY')}
                    >
                        📅 Por Día
                    </button>
                    <button
                        className={`toggle-btn-massive ${granularity === 'MONTH' ? 'active' : ''}`}
                        onClick={() => setGranularity('MONTH')}
                    >
                        📅 Por Mes
                    </button>
                    <button
                        className={`toggle-btn-massive ${granularity === 'YEAR' ? 'active' : ''}`}
                        onClick={() => setGranularity('YEAR')}
                    >
                        📅 Por Año
                    </button>
                </div>

                <div className="date-selectors">
                    {granularity === 'DAY' && (
                        <div className="filter-group">
                            <label>Día:</label>
                            <input
                                type="date"
                                value={date}
                                onChange={(e) => setDate(e.target.value)}
                            />
                        </div>
                    )}

                    {(granularity === 'MONTH' || granularity === 'YEAR') && (
                        <div className="filter-group">
                            <label>Año:</label>
                            <input
                                type="number"
                                value={year}
                                onChange={(e) => setYear(parseInt(e.target.value, 10))}
                                min="2020" max="2100"
                                style={{ width: '90px' }}
                            />
                        </div>
                    )}

                    {granularity === 'MONTH' && (
                        <div className="filter-group">
                            <label>Mes:</label>
                            <select
                                value={month}
                                onChange={(e) => setMonth(parseInt(e.target.value, 10))}
                            >
                                {[...Array(12)].map((_, i) => (
                                    <option key={i + 1} value={i + 1}>
                                        {new Date(0, i).toLocaleString('es', { month: 'long' }).toUpperCase()}
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}
                </div>
            </div>


            {/* Visual Blocks */}
            {loading ? (
                <div className="loading-state">Calculando reportes...</div>
            ) : stats ? (
                <>
                    {/* SECTION 1: Rendimiento Comercial */}
                    <div className="report-section comercial">
                        <h2 className="report-section-title">📦 RENDIMIENTO COMERCIAL (Acumulado)</h2>
                        <div className="stats-grid">
                            <div className="stat-card card-revenue">
                                <h3>Total Ventas (Ingresos)</h3>
                                <div className="value">{formatCurrency(rc.ingresosVentas)}</div>
                                <div className="sub-value">Incluye ventas cobradas y fiadas</div>
                            </div>

                            <div className="stat-card card-cogs">
                                <h3>Costo de Mercadería</h3>
                                <div className="value">{formatCurrency(rc.costoTotalVendido)}</div>
                                <div className="sub-value">Precio de costo al momento de venta</div>
                            </div>

                            <div className="stat-card card-debt">
                                <h3>Deudas Pendientes</h3>
                                <div className="value">{formatCurrency(rc.deudasPendientes)}</div>
                                <div className="sub-value">Total histórico de fiados activos</div>
                            </div>

                            <div className="stat-card card-profit">
                                <h3>Ganancia Bruta</h3>
                                <div className="value">{formatCurrency(gananciaBruta)}</div>
                                <div className="sub-value">Margen comercial del {margenPct.toFixed(1)}%</div>
                            </div>

                            <div className="stat-card">
                                <h3>Volumen de Productos</h3>
                                <div className="value">{rc.productosVendidos} <span style={{color: '#64748b'}}>vendidos</span></div>
                                <div className="sub-value">{rc.productosComprados} comprados al proveedor</div>
                            </div>
                        </div>
                    </div>

                    {/* SECTION 2: Flujo de Caja (Cash Flow) */}
                    <div className="report-section caja">
                        <h2 className="report-section-title">💵 REPORTE DE CAJA (Efectivo Real)</h2>
                        <div className="stats-grid">
                            <div className="stat-card card-cashin">
                                <h3>Dinero Entrante</h3>
                                <div className="value">{formatCurrency(fc.ingresosEfectivo)}</div>
                                <div className="sub-value">Ventas al contado + Pagos de Deudas + Señas</div>
                            </div>

                            <div className="stat-card card-cashout">
                                <h3>Dinero Saliente</h3>
                                <div className="value">{formatCurrency(fc.egresosEfectivo)}</div>
                                <div className="sub-value">Pagos a proveedores por compras</div>
                            </div>

                            <div className="stat-card card-net">
                                <h3>Balance Neto</h3>
                                <div className="value">{formatCurrency(fc.balanceNeto)}</div>
                                <div className="sub-value">Ingresos de Caja - Egresos de Caja</div>
                            </div>
                        </div>
                    </div>
                </>
            ) : (
                <div className="empty-state">No se pudieron cargar los datos.</div>
            )}
        </div>
    );
}

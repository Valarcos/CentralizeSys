import { useState, useEffect } from 'react';
import api from '../services/api';
import { formatCurrency, formatDate } from '../utils/format';
import SalesDetailModal from '../components/SalesDetailModal';
import toast from 'react-hot-toast';
import './SalesHistoryPage.css';

export default function SalesHistoryPage() {
    // Default: Last 30 days
    const [startDate, setStartDate] = useState(() => {
        const d = new Date();
        d.setDate(d.getDate() - 30);
        return d.toISOString().split('T')[0];
    });
    const [endDate, setEndDate] = useState(() => new Date().toISOString().split('T')[0]);

    const [page, setPage] = useState(0);
    const [pageSize] = useState(15);
    const [sales, setSales] = useState([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [selectedSale, setSelectedSale] = useState(null);

    useEffect(() => {
        loadSales();
    }, [page]); // Reload when page changes. Date changes are manual via specific button or effect?
    // Plan: Reload when page changes OR when user clicks "Filtrar" or auto-reload on date change if valid?
    // User Req: "when the time period is chosen, the list should refresh"
    // Let's reload on date change (debounced or valid) OR explicit button.
    // Explicit button is safer for UX with two inputs. Let's add a "Buscar" button or use useEffect with validation.
    // Let's use useEffect on dates but only if valid.

    // Removed auto-fetch useEffect for dates to prevent disruptive toasts while selecting

    const handleSearch = () => {
        if (validateDateRange(startDate, endDate, true)) {
            setPage(0);
            loadSales(0);
        }
    };

    const validateDateRange = (start, end, showToast = true) => {
        const d1 = new Date(start);
        const d2 = new Date(end);
        const diffTime = Math.abs(d2 - d1);
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (d1 > d2) {
            if (showToast) toast.error("Fecha inicio no puede ser mayor a fin");
            return false;
        }
        if (diffDays > 60) {
            if (showToast) toast.error("El rango de fechas elegido no debe superar los 60 días");
            return false;
        }
        return true;
    };

    const loadSales = async (pageOverride = null) => {
        setLoading(true);
        try {
            const currentPage = pageOverride !== null ? pageOverride : page;
            const params = {
                page: currentPage,
                size: pageSize,
                startDate,
                endDate
            };

            const res = await api.get('/api/ventas', { params });
            // New PageResponse structure
            setSales(res.data.content);
            setTotalPages(res.data.totalPages);
            setTotalElements(res.data.totalElements);
        } catch (error) {
            console.error("Error loading sales", error);
            toast.error(error.response?.data?.message || "Error al cargar historial");
        } finally {
            setLoading(false);
        }
    };

    const handleOpenDetails = async (saleId) => {
        try {
            const res = await api.get(`/api/ventas/${saleId}`);
            setSelectedSale(res.data);
        } catch (error) {
            console.error("Error fetching sale details", error);
        }
    };

    return (
        <div className="history-page container">
            <div className="history-header-column">
                <h1>Historial de Ventas</h1>

                {/* 1. Date Filters Row (Above Pagination) */}
                <div className="date-filters-row">
                    <div className="filter-group">
                        <label>Desde:</label>
                        <input
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            className="date-filter"
                        />
                    </div>
                    <div className="filter-group">
                        <label>Hasta:</label>
                        <input
                            type="date"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            className="date-filter"
                        />
                    </div>
                    <button onClick={handleSearch} className="primary" style={{ height: '38px', alignSelf: 'flex-end' }}>
                        Filtrar
                    </button>
                </div>

                {/* 2. Pagination Row (Label Left | Buttons Right) */}
                <div className="pagination-row">
                    <span className="total-label">
                        Total: {totalElements} ventas
                    </span>

                    <div className="pagination-controls">
                        <button
                            onClick={() => setPage(p => p - 1)}
                            disabled={page === 0}
                            className="secondary"
                        >
                            ← Anterior
                        </button>
                        <span className="page-indicator">
                            {page + 1} / {totalPages || 1}
                        </span>
                        <button
                            onClick={() => setPage(p => p + 1)}
                            disabled={page === (totalPages - 1) || totalPages === 0}
                            className="secondary"
                        >
                            Siguiente →
                        </button>
                    </div>
                </div>
            </div>

            {loading ? (
                <p>Cargando ventas...</p>
            ) : (
                <>
                    <div className="table-responsive">
                        <table className="history-table">
                            <thead>
                            <tr>
                                <th>ID</th>
                                <th>Fecha</th>
                                <th>Cliente</th>
                                <th>Tipo</th>
                                <th>Total</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {sales.map(sale => (
                                <tr key={sale.id}>
                                    <td data-label="ID">#{sale.id}</td>
                                    <td data-label="Fecha">{formatDate(sale.fecha)}</td>
                                    <td data-label="Cliente">{sale.clienteNombre || 'Consumidor Final'}</td>
                                    <td data-label="Tipo">
                                            <span className={`badge ${sale.tipoVenta === 'MAYORISTA' ? 'badge-wholesale' : 'badge-retail'}`}>
                                                {sale.tipoVenta || 'ESTÁNDAR'}
                                            </span>
                                    </td>
                                    <td data-label="Total" className="amount-cell">{formatCurrency(sale.totalVenta)}</td>
                                    <td data-label="Acciones">
                                        <button className="btn-details" onClick={() => handleOpenDetails(sale.id)}>
                                            Ver
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            {sales.length === 0 && (
                                <tr>
                                    <td colSpan="6" style={{ textAlign: 'center' }}>No se encontraron ventas en este período.</td>
                                </tr>
                            )}
                            </tbody>
                        </table>


                    </div>


                    {/* Bottom Pagination - Aligned Right */}
                    {totalPages > 1 && (
                        <div className="pagination-controls bottom-pagination">
                            <button
                                onClick={() => setPage(p => p - 1)}
                                disabled={page === 0}
                                className="secondary"
                            >
                                ← Anterior
                            </button>
                            <span className="page-indicator">
                                {page + 1} / {totalPages || 1}
                            </span>
                            <button
                                onClick={() => setPage(p => p + 1)}
                                disabled={page === (totalPages - 1) || totalPages === 0}
                                className="secondary"
                            >
                                Siguiente →
                            </button>
                        </div>
                    )}

                </>
            )}

            {selectedSale && (
                <SalesDetailModal
                    sale={selectedSale}
                    onClose={() => setSelectedSale(null)}
                />
            )}
        </div>
    );
}

import { useState, useEffect } from 'react';
import api from '../services/api';
import { formatCurrency, formatDate } from '../utils/format';
import SalesDetailModal from '../components/SalesDetailModal';
import './SalesHistoryPage.css';

export default function SalesHistoryPage() {
    const [sales, setSales] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filterDate, setFilterDate] = useState('');
    const [selectedSale, setSelectedSale] = useState(null);

    useEffect(() => {
        loadSales();
    }, []);

    const loadSales = async () => {
        try {
            const res = await api.get('/api/ventas');
            // Sort by ID desc (newest first)
            const sorted = res.data.sort((a, b) => b.id - a.id);
            setSales(sorted);
        } catch (error) {
            console.error("Error loading sales", error);
        } finally {
            setLoading(false);
        }
    };

    const filteredSales = sales.filter(sale => {
        if (!filterDate) return true;
        return sale.fecha.startsWith(filterDate);
    });

    const handleOpenDetails = async (saleId) => {
        try {
            // Fetch full details (the list only has headers if optimization was applied, 
            // but VentaController.getAll() currently returns everything. 
            // Good practice to fetch by ID if list is light, but for now we use what we have 
            // OR fetch specific ID to be safe if getAll becomes lightweight).
            // Let's fetch the individual sale to be sure we get everything including details.
            const res = await api.get(`/api/ventas/${saleId}`);
            setSelectedSale(res.data);
        } catch (error) {
            console.error("Error fetching sale details", error);
        }
    };

    return (
        <div className="history-page container">
            <div className="history-header">
                <h1>Historial de Ventas</h1>
                <input
                    type="date"
                    value={filterDate}
                    onChange={(e) => setFilterDate(e.target.value)}
                    className="date-filter"
                />
            </div>

            {loading ? (
                <p>Cargando ventas...</p>
            ) : (
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
                        {filteredSales.map(sale => (
                            <tr key={sale.id}>
                                <td>#{sale.id}</td>
                                <td>{formatDate(sale.fecha)}</td>
                                <td>{sale.clienteNombre || 'Consumidor Final'}</td>
                                <td>
                                        <span className={`badge ${sale.tipoVenta === 'MAYORISTA' ? 'badge-wholesale' : 'badge-retail'}`}>
                                            {sale.tipoVenta || 'ESTÁNDAR'}
                                        </span>
                                </td>
                                <td className="amount-cell">{formatCurrency(sale.totalVenta)}</td>
                                <td>
                                    <button className="btn-details" onClick={() => handleOpenDetails(sale.id)}>
                                        Ver
                                    </button>
                                </td>
                            </tr>
                        ))}
                        {filteredSales.length === 0 && (
                            <tr>
                                <td colSpan="5" style={{ textAlign: 'center' }}>No se encontraron ventas.</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
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

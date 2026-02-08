import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import './StockManagementModal.css';

/**
 * Modal for managing stock of a specific product across locations.
 * Shows current stock per location and allows adding/subtracting.
 */
export default function StockManagementModal({ product, onClose, onSuccess }) {
    const [stockLocations, setStockLocations] = useState([]);
    const [allLocations, setAllLocations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [adjusting, setAdjusting] = useState(false);

    // Adjustment form state
    const [selectedLocation, setSelectedLocation] = useState('');
    const [quantity, setQuantity] = useState('');
    const [operation, setOperation] = useState('add'); // 'add' or 'subtract'

    // Load stock data and locations
    useEffect(() => {
        const loadData = async () => {
            try {
                setLoading(true);
                const [stockRes, locationsRes] = await Promise.all([
                    api.get(`/api/stock/producto/${product.id}`),
                    api.get('/api/locations')
                ]);
                setStockLocations(stockRes.data);
                setAllLocations(locationsRes.data);

                // Default to first location if available
                if (locationsRes.data.length > 0) {
                    setSelectedLocation(locationsRes.data[0].id.toString());
                }
            } catch (error) {
                console.error('Error loading stock data:', error);
                toast.error('Error al cargar datos de stock');
            } finally {
                setLoading(false);
            }
        };
        loadData();
    }, [product.id]);

    // Refresh stock after adjustment
    const refreshStock = async () => {
        try {
            const response = await api.get(`/api/stock/producto/${product.id}`);
            setStockLocations(response.data);
        } catch (error) {
            console.error('Error refreshing stock:', error);
        }
    };

    const handleAdjust = async (e) => {
        e.preventDefault();

        const qty = parseInt(quantity, 10);
        if (!qty || qty <= 0) {
            toast.error('Ingrese una cantidad válida mayor a 0');
            return;
        }

        if (!selectedLocation) {
            toast.error('Seleccione una ubicación');
            return;
        }

        try {
            setAdjusting(true);
            const endpoint = operation === 'add' ? '/api/stock/add' : '/api/stock/subtract';

            await api.post(endpoint, {
                productoId: product.id,
                ubicacionId: parseInt(selectedLocation, 10),
                cantidad: qty
            });

            const actionText = operation === 'add' ? 'agregadas' : 'quitadas';
            toast.success(`${qty} unidades ${actionText} correctamente`);

            // Reset form and refresh
            setQuantity('');
            await refreshStock();

            if (onSuccess) {
                onSuccess();
            }
        } catch (error) {
            console.error('Error adjusting stock:', error);
            const message = error.response?.data?.message || 'Error al ajustar stock';
            toast.error(message);
        } finally {
            setAdjusting(false);
        }
    };

    // Get stock for a specific location
    const getStockForLocation = (locationId) => {
        const stockEntry = stockLocations.find(s => s.ubicacionId === locationId);
        return stockEntry ? stockEntry.cantidad : 0;
    };

    // Compute total stock from stockLocations (auto-refreshes after adjustments)
    const totalStock = stockLocations.reduce((sum, loc) => sum + loc.cantidad, 0);

    return (
        <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
            <div className="stock-modal" onClick={e => e.stopPropagation()}>
                <div className="stock-modal-header">
                    <p className="product-info">
                        <strong>{product.descripcion}</strong>
                        {product.codigo && <span className="product-code"> (Cód: {product.codigo})</span>}
                    </p>
                </div>

                {loading ? (
                    <div className="stock-loading">Cargando...</div>
                ) : (
                    <>
                        {/* Current Stock by Location */}
                        <div className="stock-modal-body">
                            <h3>Stock por Ubicación</h3>
                            {stockLocations.length === 0 ? (
                                <p className="no-stock-message">
                                    Este producto no tiene stock asignado a ninguna ubicación.
                                </p>
                            ) : (
                                <table className="stock-table">
                                    <thead>
                                    <tr>
                                        <th>Ubicación</th>
                                        <th>Cantidad</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {stockLocations.map((loc) => (
                                        <tr key={loc.id} className={loc.cantidad < 0 ? 'negative-stock' : ''}>
                                            <td>{loc.nombreUbicacion}</td>
                                            <td className={loc.cantidad < 0 ? 'negative' : ''}>
                                                {loc.cantidad} unidades
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                    <tfoot>
                                    <tr className="total-row">
                                        <td><strong>Total</strong></td>
                                        <td><strong>{totalStock} unidades</strong></td>
                                    </tr>
                                    </tfoot>
                                </table>
                            )}
                        </div>

                        {/* Adjustment Form */}
                        <form onSubmit={handleAdjust} className="adjust-form">
                            <h3>Ajustar Stock</h3>

                            <div className="form-row">
                                <div className="form-group">
                                    <label htmlFor="location-select">Ubicación</label>
                                    <select
                                        id="location-select"
                                        value={selectedLocation}
                                        onChange={(e) => setSelectedLocation(e.target.value)}
                                        required
                                    >
                                        {allLocations.map((loc) => (
                                            <option key={loc.id} value={loc.id}>
                                                {loc.nombre} ({getStockForLocation(loc.id)} uds)
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="quantity-input">Cantidad</label>
                                    <input
                                        id="quantity-input"
                                        type="number"
                                        min="1"
                                        value={quantity}
                                        onChange={(e) => setQuantity(e.target.value)}
                                        placeholder="Ej: 10"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="operation-buttons">
                                <button
                                    type="submit"
                                    className={`adjust-btn add ${operation === 'add' ? 'selected' : ''}`}
                                    onClick={() => setOperation('add')}
                                    disabled={adjusting}
                                >
                                    ➕ Agregar
                                </button>
                                <button
                                    type="submit"
                                    className={`adjust-btn subtract ${operation === 'subtract' ? 'selected' : ''}`}
                                    onClick={() => setOperation('subtract')}
                                    disabled={adjusting}
                                >
                                    ➖ Quitar
                                </button>
                            </div>
                        </form>
                    </>
                )}

                <div className="stock-modal-actions">
                    <button onClick={onClose} className="secondary" autoFocus>
                        Cerrar
                    </button>
                </div>
            </div>
        </div>
    );
}

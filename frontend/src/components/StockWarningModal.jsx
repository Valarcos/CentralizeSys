import { useState, useEffect } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import './StockWarningModal.css';

export default function StockWarningModal({ affectedProducts, onClose, onContinue, onStockCorrected }) {
    const [editingProduct, setEditingProduct] = useState(null);
    const [locations, setLocations] = useState([]);
    const [selectedLocation, setSelectedLocation] = useState('');
    const [adjustQuantity, setAdjustQuantity] = useState('');
    const [loadingLocs, setLoadingLocs] = useState(false);

    useEffect(() => {
        if (editingProduct) {
            setLoadingLocs(true);
            api.get(`/api/stock/producto/${editingProduct.id}`)
                .then(res => {
                    setLocations(res.data);
                    // Issue #9 fix: use ubicacionId (FK to ubicaciones), NOT id (the stock_por_ubicacion row PK)
                    if (res.data.length > 0) setSelectedLocation(res.data[0].ubicacionId);
                })
                .catch(err => {
                    console.error("Error fetching locations", err);
                    toast.error("Error al cargar ubicaciones");
                })
                .finally(() => setLoadingLocs(false));
        }
    }, [editingProduct]);

    const handleCorrectStock = async () => {
        if (!selectedLocation || !adjustQuantity || parseInt(adjustQuantity) <= 0) {
            toast.error("Datos inválidos");
            return;
        }

        try {
            await api.post('/api/stock/add', {
                productoId: editingProduct.id,
                ubicacionId: parseInt(selectedLocation),
                cantidad: parseInt(adjustQuantity)
            });
            toast.success("Stock corregido exitosamente");
            setEditingProduct(null);
            // Issue #3: Refresh the product in parent so it can re-evaluate and close if needed
            if (onStockCorrected) onStockCorrected();
        } catch (error) {
            console.error("Error adjusting stock", error);
            toast.error("Error al corregir stock");
        }
    };

    if (!affectedProducts || affectedProducts.length === 0) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content warning-modal">
                <div className="modal-header">
                    <h2>⚠️ Advertencia de Stock Negativo</h2>
                </div>

                {!editingProduct ? (
                    <>
                        <p>Los siguientes productos tienen stock insuficiente:</p>
                        <ul className="warning-list">
                            {affectedProducts.map(p => (
                                <li key={p.id} className="warning-item">
                                    <div className="warning-info">
                                        <span className="warning-name">{p.descripcion}</span>
                                        <span className="warning-stock">Stock Actual: {p.cantidadStock} | Productos a vender: {p.cartQuantity}</span>
                                    </div>
                                    <button
                                        className="correct-btn"
                                        onClick={() => setEditingProduct(p)}
                                    >
                                        Corregir
                                    </button>
                                </li>
                            ))}
                        </ul>
                        <div className="modal-actions">
                            <button className="cancel-btn" onClick={onClose}>Cancelar Venta</button>
                            <button className="continue-btn" onClick={onContinue}>Ignorar y Continuar</button>
                        </div>
                    </>
                ) : (
                    <div className="adjustment-screen">
                        <h3>Corregir: {editingProduct.descripcion}</h3>
                        <p>Agregar stock para cubrir la venta.</p>

                        {loadingLocs ? <p>Cargando ubicaciones...</p> : (
                            <div className="form-group">
                                <label>Ubicación:</label>
                                <select
                                    value={selectedLocation}
                                    onChange={e => setSelectedLocation(e.target.value)}
                                    style={{ width: '100%', padding: '0.5rem', marginBottom: '1rem' }}
                                >
                                    {locations.map(loc => (
                                        <option key={loc.id} value={loc.ubicacionId}>
                                            {loc.nombreUbicacion || `Ubicación ${loc.ubicacionId}`} (Actual: {loc.cantidad})
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}

                        <div className="form-group">
                            <label>Cantidad a Agregar:</label>
                            <input
                                type="number"
                                value={adjustQuantity}
                                onChange={e => setAdjustQuantity(e.target.value)}
                                min="1"
                                style={{ width: '100%', padding: '0.5rem', marginBottom: '1rem' }}
                            />
                        </div>

                        <div className="modal-actions">
                            <button className="cancel-btn" onClick={() => setEditingProduct(null)}>Volver</button>
                            <button className="save-btn" onClick={handleCorrectStock}>Guardar Corrección</button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

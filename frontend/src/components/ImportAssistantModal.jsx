import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { blockNonNumericKeys, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';

export default function ImportAssistantModal({ onClose, onConfirm, totalCompraBase = 0, isStandalone = false }) {
    const [baseValue, setBaseValue] = useState('');
    const [costoFinal, setCostoFinal] = useState('');
    const [searchFactura, setSearchFactura] = useState('');
    const [isSearching, setIsSearching] = useState(false);
    const [compraAsociada, setCompraAsociada] = useState(null);

    useEffect(() => {
        if (!isStandalone && totalCompraBase > 0) {
            setBaseValue(totalCompraBase.toFixed(2));
        }
    }, [totalCompraBase, isStandalone]);

    const parsedBase = parseFloat(baseValue || '0');
    const diferencia = parseFloat(costoFinal || '0') - parsedBase;
    const tieneDiferencia = diferencia > 0.01;

    const handleSearch = async () => {
        if (!searchFactura.trim()) return;
        setIsSearching(true);
        try {
            const response = await api.get(`/compras/factura/${encodeURIComponent(searchFactura.trim())}`);
            const compra = response.data;
            setCompraAsociada(compra);

            const totalPagado = compra.totalCompra + (compra.totalAjustes || 0);
            setBaseValue(totalPagado.toFixed(2));

            if (compra.totalAjustes > 0) {
                toast.success(`Factura encontrada. Tiene $${compra.totalAjustes.toFixed(2)} en ajustes previos.`);
            } else {
                toast.success("Factura encontrada y vinculada.");
            }
        } catch (error) {
            if (error.response?.status === 404) {
                toast.error("Factura no encontrada.");
            } else {
                toast.error("Error al buscar la factura.");
            }
            setCompraAsociada(null);
            setBaseValue('');
        } finally {
            setIsSearching(false);
        }
    };

    const handleConfirm = () => {
        if (!tieneDiferencia) {
            toast.error("El costo final debe ser mayor al costo base de los productos.");
            return;
        }

        onConfirm(diferencia, parseFloat(costoFinal), compraAsociada);
        onClose();
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content" style={{ padding: '2rem', maxWidth: '400px', width: '100%', background: '#fff', borderRadius: '8px' }}>
                <h2>⛴️ Asistente de Importación</h2>
                <p style={{ marginBottom: '1rem', color: '#666' }}>
                    Calcula la diferencia por impuestos o flete. Se registrará como un ajuste.
                </p>

                {isStandalone && (
                    <div className="form-group" style={{ marginBottom: '1rem', background: '#f8f9fa', padding: '10px', borderRadius: '4px' }}>
                        <label>Vincular a Factura (Opcional):</label>
                        <div style={{ display: 'flex', gap: '5px', marginTop: '0.5rem' }}>
                            <input
                                type="text"
                                className="form-control"
                                value={searchFactura}
                                onChange={e => setSearchFactura(e.target.value)}
                                placeholder="Nro Factura (Ej: 0001-0005)"
                                style={{ flex: 1, padding: '0.5rem' }}
                                onKeyDown={e => e.key === 'Enter' && handleSearch()}
                            />
                            <button
                                onClick={handleSearch}
                                disabled={isSearching || !searchFactura.trim()}
                                className="btn-secondary"
                                type="button"
                            >
                                {isSearching ? 'Buscando...' : '🔍 Buscar'}
                            </button>
                        </div>
                        {compraAsociada && (
                            <div style={{ marginTop: '0.5rem', fontSize: '0.9rem', color: '#2e7d32' }}>
                                ✅ Vinculada a compra: {compraAsociada.proveedor}
                                {compraAsociada.totalAjustes > 0 && (
                                    <div style={{ marginTop: '0.5rem', padding: '0.5rem', background: '#fff3cd', color: '#856404', borderRadius: '4px', border: '1px solid #ffeeba' }}>
                                        ⚠️ Esta factura ya tiene <strong>${compraAsociada.totalAjustes.toFixed(2)}</strong> en ajustes previos. El Total Base debajo refleja el monto original más los ajustes previos.
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}

                <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label>Total Base (Suma de Costos):</label>
                    {isStandalone && !compraAsociada ? (
                        <input
                            type="text"
                            inputMode="decimal"
                            className="form-control"
                            value={baseValue}
                            onChange={e => setBaseValue(enforceMoneyFormat(e.target.value))}
                            onKeyDown={blockNonNumericKeys}
                            onPaste={sanitizeNumericPaste}
                            placeholder="0.00"
                            style={{ width: '100%', padding: '0.5rem', fontSize: '1.1rem', marginTop: '0.5rem' }}
                        />
                    ) : (
                        <div style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>
                            ${parsedBase.toFixed(2)}
                        </div>
                    )}
                </div>

                <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label>Costo Real Final (Factura total con impuestos):</label>
                    <input
                        type="text"
                        inputMode="decimal"
                        className="form-control"
                        value={costoFinal}
                        onChange={e => setCostoFinal(enforceMoneyFormat(e.target.value))}
                        onKeyDown={blockNonNumericKeys}
                        onPaste={sanitizeNumericPaste}
                        placeholder="0.00"
                        style={{ width: '100%', padding: '0.5rem', fontSize: '1.1rem', marginTop: '0.5rem' }}
                    />
                </div>

                {tieneDiferencia && (
                    <div style={{ padding: '1rem', background: '#e8f5e9', color: '#2e7d32', borderRadius: '4px', marginBottom: '1rem' }}>
                        Diferencia a registrar como Gasto de Caja:
                        <strong style={{ display: 'block', fontSize: '1.2rem' }}>${diferencia.toFixed(2)}</strong>
                    </div>
                )}

                <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
                    <button onClick={onClose} style={{ padding: '0.5rem 1rem', background: '#ccc', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
                        Cancelar
                    </button>
                    <button
                        onClick={handleConfirm}
                        disabled={!tieneDiferencia}
                        style={{ padding: '0.5rem 1rem', background: tieneDiferencia ? '#007bff' : '#ccc', color: 'white', border: 'none', borderRadius: '4px', cursor: tieneDiferencia ? 'pointer' : 'not-allowed' }}
                    >
                        Confirmar Diferencia
                    </button>
                </div>
            </div>
        </div>
    );
}

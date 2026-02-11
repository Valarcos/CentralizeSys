import { useState, useEffect } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import VariantConfirmationModal from './VariantConfirmationModal';
import ProductFormModal from './ProductFormModal';
import './StockEntryModal.css';

export default function StockEntryModal({ onClose, onSuccess }) {
    const [provider, setProvider] = useState('');
    const [invoiceNo, setInvoiceNo] = useState('');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [locations, setLocations] = useState([]);
    const [selectedLocationId, setSelectedLocationId] = useState('');

    // Draft Items: { product, quantity, cost, error }
    const [draftItems, setDraftItems] = useState([]);

    // Variant Handling State
    const [verifyingIndex, setVerifyingIndex] = useState(null); // Index of item being verified
    const [showVariantModal, setShowVariantModal] = useState(false);
    const [showProductForm, setShowProductForm] = useState(false);
    const [variantSourceProduct, setVariantSourceProduct] = useState(null);

    useEffect(() => {
        // Fetch Locations
        const fetchLocations = async () => {
            try {
                const res = await api.get('/api/locations');
                setLocations(res.data);
                if (res.data.length > 0) {
                    setSelectedLocationId(res.data[0].id);
                }
            } catch (err) {
                console.error("Error fetching locations", err);
                toast.error("Error al cargar ubicaciones");
            }
        };
        fetchLocations();
    }, []);

    // --- SEARCH LOGIC ---
    useEffect(() => {
        const fetchProducts = async () => {
            if (!searchQuery) {
                setSearchResults([]);
                return;
            }
            try {
                const res = await api.get('/api/productos', { params: { search: searchQuery, size: 20 } });
                setSearchResults(res.data.content);
            } catch (err) {
                console.error(err);
            }
        };
        const timeout = setTimeout(fetchProducts, 300);
        return () => clearTimeout(timeout);
    }, [searchQuery]);

    // --- HANDLERS ---
    const addToDraft = (product) => {
        if (draftItems.some(item => item.product.id === product.id)) {
            toast.error("El producto ya está en la lista");
            return;
        }

        setDraftItems(prev => [...prev, {
            product,
            quantity: 1,
            cost: product.precioCosto,
            error: null
        }]);
        setSearchQuery('');
    };

    const updateItem = (index, field, value) => {
        const newItems = [...draftItems];
        const item = newItems[index];

        if (field === 'quantity') {
            item.quantity = parseInt(value) || 0;
            setDraftItems(newItems);
        } else if (field === 'cost') {
            const newCost = parseFloat(value) || 0;
            item.cost = newCost;

            // VALIDATION with Modal
            const dbCost = item.product.precioCosto;
            if (Math.abs(newCost - dbCost) > 0.01) {
                // Mark error blue (warning)
                item.error = "Costo modificado. Verificando...";
                // Trigger Modal
                setVerifyingIndex(index);
                setShowVariantModal(true);
            } else {
                item.error = null;
            }
            setDraftItems(newItems);
        }
    };

    const handleConfirmVariant = () => {
        // User wants to create variant
        const item = draftItems[verifyingIndex];
        setVariantSourceProduct(item.product);
        setShowVariantModal(false);
        setShowProductForm(true); // Open ProductForm
    };

    const handleCorrectCost = () => {
        // User wants to correct manual input.
        // We just close the modal. The input stays "blue" or invalid until they fix it logic-wise?
        // User requirements: "Input remains Red/Invalid. Button Disabled."
        // We set error state to something persistent.
        const newItems = [...draftItems];
        newItems[verifyingIndex].error = `Costo difiere (DB: ${draftItems[verifyingIndex].product.precioCosto}). Corrija o cree variante.`;
        setDraftItems(newItems);
        setShowVariantModal(false);
        setVerifyingIndex(null);
    };

    const handleVariantCreated = (newProduct) => {
        // Replace the item in draft with the new product
        const newItems = [...draftItems];
        newItems[verifyingIndex] = {
            product: newProduct,
            quantity: newItems[verifyingIndex].quantity,
            cost: newProduct.precioCosto, // Should match what they just created
            error: null
        };
        setDraftItems(newItems);
        setShowProductForm(false);
        setVerifyingIndex(null);
        setVariantSourceProduct(null);
    };

    const removeItem = (index) => {
        setDraftItems(prev => prev.filter((_, i) => i !== index));
    };

    const handleSubmit = async () => {
        if (!provider || !invoiceNo) {
            toast.error("Complete Proveedor y Nro Comprobante");
            return;
        }

        if (!selectedLocationId) {
            toast.error("Seleccione una ubicación de destino");
            return;
        }

        const hasErrors = draftItems.some(i => i.error);
        if (hasErrors) {
            toast.error("Hay items con diferencias de costo. Corrija o cree variantes.");
            return;
        }

        if (draftItems.length === 0) {
            toast.error("La lista está vacía");
            return;
        }

        try {
            const payload = {
                proveedor: provider,
                nroComprobante: invoiceNo,
                usuarioId: 1, // ToDo: Auth
                items: draftItems.map(i => ({
                    productoId: i.product.id,
                    cantidad: i.quantity,
                    costoUnitario: i.cost,
                    ubicacionId: parseInt(selectedLocationId)
                }))
            };

            await api.post('/api/compras', payload);
            toast.success("Ingreso Registrado Correctamente");
            onSuccess();
        } catch (error) {
            console.error(error);
            toast.error(error.response?.data?.message || "Error al registrar ingreso");
        }
    };

    const total = draftItems.reduce((sum, item) => sum + (item.quantity * item.cost), 0);

    return (
        <div className="stock-entry-modal-overlay">
            <div className="stock-entry-modal">
                <div className="modal-header">
                    <h2>📥 Registrar Ingreso de Stock</h2>
                    <button onClick={onClose} className="close-btn">×</button>
                </div>

                <div className="modal-body-layout">
                    {/* LEFT: Search & Inputs */}
                    <div className="left-panel">
                        <div className="form-group-row">
                            <input
                                className="provider-input"
                                placeholder="Proveedor"
                                value={provider}
                                onChange={e => setProvider(e.target.value)}
                            />
                            <input
                                className="invoice-input"
                                placeholder="Nro Factura"
                                value={invoiceNo}
                                onChange={e => setInvoiceNo(e.target.value)}
                            />
                        </div>

                        <div style={{ marginBottom: '1rem' }}>
                            <select
                                className="location-select"
                                value={selectedLocationId}
                                onChange={e => setSelectedLocationId(e.target.value)}
                            >
                                {locations.map(loc => (
                                    <option key={loc.id} value={loc.id}>{loc.nombre}</option>
                                ))}
                            </select>
                        </div>

                        <input
                            className="search-bar-large"
                            placeholder="🔍 Buscar producto a reponer..."
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            autoFocus
                        />

                        <div className="search-results">
                            {searchResults.map(p => (
                                <div key={p.id} className="search-item" onClick={() => addToDraft(p)}>
                                    <strong>{p.descripcion}</strong>
                                    <div>Costo: ${p.precioCosto}</div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* RIGHT: List */}
                    <div className="right-panel">
                        <h3>Items a Ingresar ({draftItems.length})</h3>
                        <div className="draft-list-scroll">
                            {draftItems.map((item, i) => (
                                <div key={i} className={`draft-item ${item.error ? 'item-error' : ''}`}>
                                    <div className="item-info">
                                        <span className="item-name">{item.product.descripcion}</span>
                                        {item.error && <span className="error-badge">⚠️ Costo Difiere</span>}
                                    </div>
                                    <div className="item-inputs">
                                        <input
                                            type="number"
                                            className="qty-input"
                                            value={item.quantity}
                                            onChange={e => updateItem(i, 'quantity', e.target.value)}
                                            placeholder="Cant."
                                        />
                                        <input
                                            type="number"
                                            className="cost-input"
                                            value={item.cost}
                                            onBlur={e => updateItem(i, 'cost', e.target.value)}
                                            // Using onBlur for Modal trigger to avoid popup while typing?
                                            // Or duplicate state for visual vs committed?
                                            // Let's use onBlur for validation triggering to be less annoying.
                                            // But we need to update state onChange to typing works.
                                            // Actually updateItem handles logic.
                                            // If we trigger modal on every keystroke it's bad.
                                            // Let's split handle.
                                            onChange={e => {
                                                const val = e.target.value;
                                                const newItems = [...draftItems];
                                                newItems[i].cost = val; // Just update value
                                                setDraftItems(newItems);
                                            }}
                                            placeholder="Costo"
                                        />
                                        <button onClick={() => removeItem(i)} className="remove-btn">×</button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="footer-actions">
                            <div className="total-display">Total: ${total.toFixed(2)}</div>
                            <button className="submit-btn" onClick={handleSubmit}>CONFIRMAR</button>
                        </div>
                    </div>
                </div>
            </div>

            {showVariantModal && (
                <VariantConfirmationModal
                    currentCost={draftItems[verifyingIndex]?.product.precioCosto}
                    newCost={draftItems[verifyingIndex]?.cost}
                    onConfirmVariant={handleConfirmVariant}
                    onCorrect={handleCorrectCost}
                />
            )}

            {showProductForm && (
                <ProductFormModal
                    product={variantSourceProduct}
                    isVariant={true}
                    onSuccess={handleVariantCreated}
                    onCancel={() => {
                        setShowProductForm(false);
                        handleCorrectCost(); // Treat cancel as "Correcting" (returning to invalid state)
                    }}
                />
            )}
        </div>
    );
}

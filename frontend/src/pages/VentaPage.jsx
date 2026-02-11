import { useState, useEffect } from 'react';
import useCart from '../hooks/useCart';
import api from '../services/api';
import toast from 'react-hot-toast';
import StockWarningModal from '../components/StockWarningModal';
import ConfirmationModal from '../components/ConfirmationModal';
import { generateReceipt } from '../utils/pdfGenerator';
import './VentaPage.css';

export default function VentaPage() {
    const {
        cartItems,
        clientName,
        setClientName,
        payments,
        saleType,
        setSaleType,
        addToCart,
        updateQuantity,
        updateItemDiscount, // New
        globalDiscount, // New
        setGlobalDiscount, // New
        removeFromCart,
        addPaymentMethod,
        removePaymentMethod,
        totals
    } = useCart();

    // ... existing state ...
    const [lastSale, setLastSale] = useState(null); // Stores successful sale data for receipt


    const [products, setProducts] = useState([]);
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeTab, setActiveTab] = useState('catalog');
    const [loading, setLoading] = useState(false);
    const [availableClients, setAvailableClients] = useState([]); // New Autocomplete State

    // Fetch Clients for Autocomplete
    useEffect(() => {
        const fetchClients = async () => {
            try {
                const res = await api.get('/api/ventas/clientes');
                setAvailableClients(res.data);
            } catch (err) {
                console.error("Error loading clients", err);
            }
        };
        fetchClients();
    }, []);

    // Stock Warning State
    const [affectedProducts, setAffectedProducts] = useState([]);
    const [showStockModal, setShowStockModal] = useState(false);

    // Debt Warning State
    const [showDebtModal, setShowDebtModal] = useState(false);

    // Payment Form State
    const [selectedMethodId, setSelectedMethodId] = useState('');
    const [paymentAmount, setPaymentAmount] = useState('');

    // --- LOGIC: Validate Stock ---
    const checkStockAvailability = () => {
        const issues = [];
        cartItems.forEach(item => {
            if (item.quantity > item.product.cantidadStock) {
                issues.push(item.product);
            }
        });
        return issues;
    };

    const handlePrePaymentCheck = () => {
        const issues = checkStockAvailability();
        if (issues.length > 0) {
            setAffectedProducts(issues);
            setShowStockModal(true);
        } else {
            // Check for Debt logic
            // User Rule: "When the button is clicked with an amount of money that is not accounted for,
            // a warning modal must appear"
            const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
            const remaining = totals.total - totalPaid;

            // Floating point safety check
            if (remaining > 0.01) {
                setShowDebtModal(true);
            } else {
                handleFinalizeSale();
            }
        }
    };

    const handleFinalizeSale = async () => {
        setShowStockModal(false); // Close modal if open
        setShowDebtModal(false);  // Close debt modal if open

        try {
            const saleData = {
                usuarioId: 1, // TODO: Get from Auth Context
                clienteNombre: clientName,
                tipoVenta: saleType,
                descuentoGlobal: globalDiscount, // NEW
                items: cartItems.map(item => ({
                    productoId: item.product.id,
                    cantidad: item.quantity,
                    valorDescuento: item.discount || 0 // NEW
                })),
                pagos: payments.map(p => ({
                    metodoPagoId: p.methodId,
                    monto: p.amount
                }))
            };

            const response = await api.post('/api/ventas', saleData);
            toast.success("Venta registrada con éxito");

            // Prepare data for Receipt
            setLastSale({
                id: response.data.id,
                date: new Date(),
                client: clientName,
                user: 'Sistema', // Todo
                saleType: saleType,
                items: cartItems.map(i => ({
                    ...i.product,
                    quantity: i.quantity,
                    unitPrice: i.unitPrice,
                    discount: i.discount // Pass discount for receipt if needed (pdfGenerator update maybe?)
                })),
                payments: payments,
                total: totals.total,
                globalDiscount: globalDiscount // NEW
            });

            // Do NOT reload yet, wait for user action in Success View
        } catch (error) {
            console.error("Sale Error:", error);
            const msg = error.response?.data?.message || "Error al procesar la venta";
            toast.error(msg);
        }
    };

    // New Handler for Reset
    const handleNewSale = () => {
        setLastSale(null);
        window.location.reload(); // Simple reset for MVP
    };

    const handlePrintReceipt = () => {
        if (lastSale) generateReceipt(lastSale);
    };

    // --- EFFECT: Fetch Payment Methods (Run Once) ---
    useEffect(() => {
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/api/ventas/metodos-pago');
                setPaymentMethods(res.data);
            } catch (error) {
                console.error("Error fetching payment methods:", error);
                // toast.error("Error al cargar métodos de pago");
            }
        };
        fetchPaymentMethods();
    }, []);

    // --- EFFECT: Fetch Products (Debounced Search) ---
    useEffect(() => {
        const fetchProducts = async () => {
            setLoading(true);
            try {
                const params = { size: 50 };
                // If search is empty, backend might return nothing or all?
                // .cursorrules says "Search" does LIMIT 100.
                if (searchQuery) params.search = searchQuery;

                const response = await api.get('/api/productos', { params });
                setProducts(response.data.content);
            } catch (error) {
                console.error("Error fetching products:", error);
                toast.error("Error al cargar productos");
            } finally {
                setLoading(false);
            }
        };

        const timeoutId = setTimeout(() => {
            fetchProducts();
        }, 300);

        return () => clearTimeout(timeoutId);
    }, [searchQuery]);

    // --- HANDLERS ---


    const [pendingSaleType, setPendingSaleType] = useState(null);
    const [pendingProductToAdd, setPendingProductToAdd] = useState(null); // New state for force add

    // MODIFIED: Intercept Add to Cart
    const handleAddToCart = (product) => {
        // Find existing quantity in cart
        const existingItem = cartItems.find(item => item.product.id === product.id);
        const currentQty = existingItem ? existingItem.quantity : 0;

        // Check against stock
        if (currentQty + 1 > product.cantidadStock) {
            setPendingProductToAdd(product);
            return; // Stop here, wait for modal confirmation
        }

        addToCart(product);
        toast.success(`Agregado: ${product.descripcion}`, { duration: 1000, position: 'bottom-left' });
    };

    const confirmForceAdd = () => {
        if (pendingProductToAdd) {
            addToCart(pendingProductToAdd);
            toast.success(`Agregado (Sin Stock): ${pendingProductToAdd.descripcion}`, { icon: '⚠️', duration: 2000, position: 'bottom-left' });
            setPendingProductToAdd(null);
        }
    };

    const handleSaleTypeChange = (type) => {
        if (cartItems.length > 0 && type !== saleType) {
            setPendingSaleType(type);
        } else {
            setSaleType(type);
        }
    };

    const confirmSaleTypeChange = () => {
        if (pendingSaleType) {
            setSaleType(pendingSaleType);
            setPendingSaleType(null);
        }
    };

    const handleAddPayment = () => {
        if (!selectedMethodId) {
            toast.error("Seleccione un método de pago");
            return;
        }
        const amount = parseFloat(paymentAmount);
        if (isNaN(amount) || amount <= 0) {
            toast.error("Ingrese un monto válido");
            return;
        }

        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        addPaymentMethod({
            methodId: method.id,
            name: method.descripcion,
            amount: amount
        });

        // Reset form
        setSelectedMethodId('');
        setPaymentAmount('');
    };

    const formatCurrency = (amount) => {
        return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(amount);
    };

    // Calculate remaining amount to pay
    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
    const remaining = totals.total - totalPaid;

    // Smart Dropdown: Filter out methods that are already used in the current payment stack
    const availableMethods = paymentMethods.filter(m => !payments.some(p => p.methodId === m.id));


    // Auto-fill amount logic: When selecting a method, autofill with remaining?
    const handleMethodSelect = (e) => {
        setSelectedMethodId(e.target.value);
        if (remaining > 0) {
            setPaymentAmount(remaining.toFixed(2));
        }
    };

    // --- RENDER ---
    if (lastSale) {
        return (
            <div className="venta-page success-view" style={{ justifyContent: 'center', alignItems: 'center', flexDirection: 'column' }}>
                <div style={{ textAlign: 'center', padding: '2rem', background: 'white', borderRadius: '8px', boxShadow: '0 4px 6px rgba(0,0,0,0.1)' }}>
                    <h2 style={{ color: 'green', fontSize: '2rem' }}>¡Venta Exitosa!</h2>
                    <p>ID: #{lastSale.id}</p>
                    <p>Total: {formatCurrency(lastSale.total)}</p>

                    <div style={{ display: 'flex', gap: '1rem', marginTop: '2rem' }}>
                        <button
                            onClick={handlePrintReceipt}
                            style={{ padding: '1rem 2rem', fontSize: '1.2rem', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                        >
                            🖨️ Imprimir Ticket
                        </button>
                        <button
                            onClick={handleNewSale}
                            style={{ padding: '1rem 2rem', fontSize: '1.2rem', background: '#28a745', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                        >
                            ✨ Nueva Venta
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="venta-page">
            <div className={`catalog-panel ${activeTab === 'catalog' ? 'active' : ''}`}>
                <div className="catalog-header">
                    <input
                        type="text"
                        className="search-bar-large"
                        placeholder="🔍 Buscar producto..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        autoFocus
                    />
                </div>
                <div className="product-grid">
                    {products.map(product => (
                        <div
                            key={product.id}
                            className="product-card"
                            onClick={() => handleAddToCart(product)}
                        >
                            <h3>{product.descripcion}</h3>
                            <div className="price">
                                {formatCurrency(saleType === 'MAYORISTA' ? product.precioMayorista : product.precioMinorista)}
                            </div>
                            <div className={`stock ${product.cantidadStock <= 0 ? 'stock-warning' : ''}`}>
                                Stock: {product.cantidadStock}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className={`ticket-panel theme-${saleType.toLowerCase()} ${activeTab === 'ticket' ? 'active' : ''}`}>
                <div className="ticket-header">
                    <h2>Ticket</h2>
                    <div className="sale-type-toggle">
                        <button className={`toggle-btn ${saleType === 'MINORISTA' ? 'active' : ''}`} onClick={() => handleSaleTypeChange('MINORISTA')}>Minorista</button>
                        <button className={`toggle-btn ${saleType === 'MAYORISTA' ? 'active' : ''}`} onClick={() => handleSaleTypeChange('MAYORISTA')}>Mayorista</button>
                    </div>
                </div>

                <div className="client-autocomplete-container">
                    <input
                        type="text"
                        list="client-suggestions"
                        className={`current-client-input ${!clientName ? 'required-empty' : ''}`}
                        placeholder={!clientName ? "Ingrese Cliente - Requerido" : "Cliente"}
                        value={clientName}
                        onChange={(e) => setClientName(e.target.value)}
                    />
                    <datalist id="client-suggestions">
                        {availableClients.map((client, index) => (
                            <option key={index} value={client} />
                        ))}
                    </datalist>
                </div>

                <div className="cart-items-list">
                    {cartItems.map((item, index) => (
                        <div key={index} className={`cart-item ${item.quantity > item.product.cantidadStock ? 'stock-warning-row' : ''}`}>
                            <div className="cart-item-info">
                                <b>{item.product.descripcion}</b>
                                <div>{formatCurrency(item.unitPrice)} x {item.quantity}</div>
                                <div style={{ fontSize: '0.8rem', marginTop: '0.2rem' }}>
                                    Desc. Unit:
                                    <input
                                        type="number"
                                        value={item.discount || ''}
                                        onChange={(e) => updateItemDiscount(item.product.id, e.target.value)}
                                        placeholder="0"
                                        style={{ width: '50px', marginLeft: '5px' }}
                                    />
                                </div>
                            </div>
                            <div className="cart-item-qty">
                                <button className="qty-btn" onClick={() => updateQuantity(item.product.id, item.quantity - 1)}>-</button>
                                <span>{item.quantity}</span>
                                <button className="qty-btn" onClick={() => updateQuantity(item.product.id, item.quantity + 1)}>+</button>
                            </div>
                            <div className="cart-item-total">
                                {formatCurrency((Math.max(0, item.unitPrice - (item.discount || 0))) * item.quantity)}
                            </div>
                            <button className="remove-btn" onClick={() => removeFromCart(item.product.id)}>×</button>
                        </div>
                    ))}
                </div>

                {/* PAYMENT STACK */}
                <div className="payment-section">
                    <div className="payment-stack">
                        {payments.map((p, i) => (
                            <div key={i} className="payment-row">
                                <span style={{ flex: 2 }}>{p.name}</span>
                                <span style={{ flex: 1, textAlign: 'right' }}>{formatCurrency(p.amount)}</span>
                                <button className="remove-btn" onClick={() => removePaymentMethod(i)}>×</button>
                            </div>
                        ))}

                        {/* New Payment Input Row */}
                        <div className="payment-row">
                            <select
                                className="payment-select"
                                value={selectedMethodId}
                                onChange={handleMethodSelect}
                            >
                                <option value="">Agregar Pago...</option>
                                {availableMethods.map(m => (
                                    <option key={m.id} value={m.id}>{m.descripcion}</option>
                                ))}
                            </select>
                            <input
                                type="number"
                                className="payment-amount"
                                placeholder="$" // placeholder
                                value={paymentAmount}
                                onChange={(e) => setPaymentAmount(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleAddPayment()}
                            />
                            <button onClick={handleAddPayment} className="add-payment-btn">+</button>
                        </div>
                    </div>

                    <div className="totals-area">
                        <div style={{ marginBottom: '0.5rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span>Desc. Global:</span>
                            <input
                                type="number"
                                value={globalDiscount || ''}
                                onChange={(e) => setGlobalDiscount(parseFloat(e.target.value) || 0)}
                                placeholder="0"
                                style={{ width: '80px', textAlign: 'right' }}
                            />
                        </div>
                        <div>
                            <div>Subtotal: {formatCurrency(totals.subtotal)}</div>
                            <div style={{ fontWeight: 'bold' }}>Total: {formatCurrency(totals.total)}</div>
                            <div style={{ color: remaining > 0.01 ? 'red' : 'green' }}>
                                {remaining > 0.01 ? `Falta: ${formatCurrency(remaining)}` : 'Cubierto'}
                            </div>
                        </div>
                        <button
                            className="pay-btn"
                            disabled={cartItems.length === 0 || payments.length === 0 || !clientName.trim()}
                            onClick={handlePrePaymentCheck}
                            style={{ opacity: (cartItems.length === 0 || payments.length === 0 || !clientName.trim()) ? 0.5 : 1 }}
                        >
                            FINALIZAR
                        </button>
                    </div>
                </div>

                {/* MODALS */}
                {pendingProductToAdd && (
                    <ConfirmationModal
                        title="⚠️ Stock Insuficiente"
                        message={`El producto "${pendingProductToAdd.descripcion}" tiene un stock de ${pendingProductToAdd.cantidadStock}. ¿Desea agregarlo de todas formas?`}
                        confirmText="Sí, Agregar"
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={confirmForceAdd}
                        onCancel={() => setPendingProductToAdd(null)}
                    />
                )}

                {pendingSaleType && (
                    <ConfirmationModal
                        title="Cambiar Tipo de Venta"
                        message="Al cambiar el tipo de venta, se recalcularán todos los precios del carrito. ¿Desea continuar?"
                        confirmText="Sí, Cambiar"
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={confirmSaleTypeChange}
                        onCancel={() => setPendingSaleType(null)}
                    />
                )}

                {showStockModal && (
                    <StockWarningModal
                        affectedProducts={affectedProducts}
                        onClose={() => setShowStockModal(false)}
                        onContinue={handleFinalizeSale}
                    />
                )}

                {showDebtModal && (
                    <ConfirmationModal
                        title="Venta con Deuda"
                        message={`Esta por registrar una venta con deuda para el cliente: ${clientName || 'Desconocido'}. ¿Desea continuar?`}
                        confirmText="Confirmar Venta"
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={handleFinalizeSale}
                        onCancel={() => setShowDebtModal(false)}
                    />
                )}
            </div>

            <div className="mobile-tabs mobile-only">
                <button className={`tab-btn ${activeTab === 'catalog' ? 'active' : ''}`} onClick={() => setActiveTab('catalog')}>Catálogo</button>
                <button className={`tab-btn ${activeTab === 'ticket' ? 'active' : ''}`} onClick={() => setActiveTab('ticket')}>Ticket ({cartItems.length})</button>
            </div>
        </div>
    );
}

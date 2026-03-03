import { useState, useEffect } from 'react';
import { useOutletContext, useBlocker } from 'react-router-dom';
import useCart from '../hooks/useCart';
import api from '../services/api';
import toast from 'react-hot-toast';
import StockWarningModal from '../components/StockWarningModal';
import ConfirmationModal from '../components/ConfirmationModal';
import { generateReceipt } from '../utils/pdfGenerator';
import { blockNonNumericKeys, blockNonIntegerKeys, sanitizeNumericPaste, sanitizeIntegerPaste, enforceMoneyFormat } from '../utils/numericInput';
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

    // Issue #63: Warn user before navigating away with unsaved cart data
    const blocker = useBlocker(cartItems.length > 0);
    useEffect(() => {
        if (blocker.state === 'blocked') {
            const leave = window.confirm('⚠️ ¿Desea salir?\n\nTiene productos en el carrito que se perderán si abandona esta página.');
            if (leave) {
                blocker.proceed();
            } else {
                blocker.reset();
            }
        }
    }, [blocker]);

    // ... existing state ...
    const [lastSale, setLastSale] = useState(null); // Stores successful sale data for receipt


    const [products, setProducts] = useState([]);
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const { salesActiveTab: activeTab } = useOutletContext();
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

    // Overpayment Modal State (Issue #8)
    const [showOverpaidModal, setShowOverpaidModal] = useState(false);
    const [overpaidMaxAllowed, setOverpaidMaxAllowed] = useState(0);

    // Issue #7 + #9: Warn when payments exceed total (due to discount or sale type change)
    useEffect(() => {
        if (totals.isOverpaid) {
            toast.error("Ajustar montos y métodos de pago", { id: 'overpaid-warning' });
        }
    }, [totals.isOverpaid]);

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
        if (isSubmitting) return;

        // 0. Empty check
        if (!cartItems || cartItems.length === 0) {
            toast.error("El carrito está vacío");
            return;
        }

        // 1. Stock Validation
        const insufficientStockItems = cartItems.filter(item => {
            const currentStock = item.product.cantidadStock || 0;
            return (currentStock - item.quantity) < 0;
        });

        if (insufficientStockItems.length > 0) {
            const confirm = window.confirm(
                `⚠️ STOCK NEGATIVO\n\nLos siguientes productos quedarán con stock negativo:\n` +
                insufficientStockItems.map(i => `- ${i.product.descripcion} (Stock: ${i.product.cantidadStock}, Venta: ${i.quantity})`).join('\n') +
                `\n\n¿Desea continuar de todas formas?`
            );
            if (!confirm) return;
        }

        // 2. Debt Validation (This logic needs to be adapted to the current `payments` and `saleType` state)
        // The original `handleFinalizeSale` already handles `saleType` and `clientName` for 'FIADO'.
        // The provided snippet's debt validation seems to be for a different state structure (`paymentMethod`, `amountPaid`).
        // I will keep the existing `setShowDebtModal` logic from `handlePrePaymentCheck` and assume it leads here.
        // If `saleType` is 'FIADO' and `clientName` is empty, it should be caught earlier or here.
        if (saleType === 'FIADO' && !clientName.trim()) {
            toast.error("Para FIADO, debe ingresar el Nombre del Cliente");
            return;
        }

        const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
        const remaining = totals.total - totalPaid;

        // 3. Payment Validation (If not FIADO, payment must cover total)
        // This part also needs adaptation. The current system uses `payments` array.
        // The `handlePrePaymentCheck` already handles the `remaining > 0.01` case by showing `setShowDebtModal`.
        // If we reach here, either `remaining` is 0 or the user confirmed debt via the modal.
        // The provided snippet's logic for `paid < total` and `confirmDebt` is for a single payment amount.
        // I will keep the existing flow where `setShowDebtModal` handles this.
        // If `saleType` is not 'FIADO' and there's a remaining balance, it should have been caught by `handlePrePaymentCheck`.

        setShowStockModal(false); // Close modal if open
        setShowDebtModal(false);  // Close debt modal if open

        try {
            setIsSubmitting(true);
            const saleData = {
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

            // Check for alerts (Negative Stock warning from backend)
            if (response.data.alertas && response.data.alertas.length > 0) {
                response.data.alertas.forEach(alert => toast(alert, { icon: '⚠️' }));
            }

            // Prepare data for Receipt
            setLastSale({
                id: response.data.id,
                date: new Date(),
                client: clientName,
                user: localStorage.getItem('userName') || 'Sistema',
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
        } finally {
            setIsSubmitting(false);
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

        // Issue #8: Detect overpayment
        const currentPaid = payments.reduce((sum, p) => sum + p.amount, 0);
        const maxAllowed = Math.max(0, totals.total - currentPaid);
        if (amount > maxAllowed + 0.01) {
            setOverpaidMaxAllowed(maxAllowed);
            setShowOverpaidModal(true);
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

    // Issue #8: Auto-correct handler for overpayment modal
    const handleAutoCorrectPayment = () => {
        setShowOverpaidModal(false);
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        if (method && overpaidMaxAllowed > 0) {
            addPaymentMethod({
                methodId: method.id,
                name: method.descripcion,
                amount: overpaidMaxAllowed
            });
            setSelectedMethodId('');
            setPaymentAmount('');
        } else {
            setPaymentAmount(overpaidMaxAllowed.toFixed(2));
        }
    };

    const formatCurrency = (amount) => {
        return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(amount);
    };

    // Calculate remaining amount to pay
    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
    const remaining = totals.total - totalPaid;

    // Smart Dropdown: Filter out methods that are already used in the current payment stack
    const availableMethods = paymentMethods.filter(m => !payments.some(p => p.methodId === m.id));


    // Auto-fill amount logic: When selecting a method, autofill with remaining
    const handleMethodSelect = (e) => {
        setSelectedMethodId(e.target.value);
        if (remaining > 0.01) {
            setPaymentAmount(remaining.toFixed(2));
        } else {
            setPaymentAmount('');
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
                            {/* Row 1: Product Name */}
                            <div className="cart-row cart-row-name">
                                <b>{item.product.descripcion}</b>
                            </div>
                            {/* Row 2: Unit Price x Qty + Discount Input */}
                            <div className="cart-row cart-row-price">
                                <span className="price-label">{formatCurrency(item.unitPrice)} x {item.quantity}</span>
                                <div className="item-discount">
                                    <label>Descuento</label>
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={item.discount || ''}
                                        onChange={(e) => {
                                            const val = enforceMoneyFormat(e.target.value);
                                            updateItemDiscount(item.product.id, val);
                                        }}
                                        onKeyDown={blockNonNumericKeys}
                                        onPaste={sanitizeNumericPaste}
                                        placeholder="$0"
                                        className="discount-input"
                                    />
                                </div>
                            </div>
                            {/* Row 3: Qty Controls + Total + Remove */}
                            <div className="cart-row cart-row-actions">
                                <div className="cart-item-qty">
                                    <button className="qty-btn" onClick={() => {
                                        const result = updateQuantity(item.product.id, item.quantity - 1);
                                        if (result === 'zero_blocked') {
                                            toast('Para eliminar producto tocar su botón ×', { icon: 'ℹ️', duration: 2000 });
                                        }
                                    }}>-</button>
                                    <input
                                        type="text"
                                        inputMode="numeric"
                                        className="qty-input"
                                        value={item.quantity}
                                        onChange={(e) => {
                                            const val = parseInt(e.target.value.replace(/[^0-9]/g, ''), 10);
                                            if (!isNaN(val) && val >= 1) {
                                                updateQuantity(item.product.id, val);
                                            }
                                        }}
                                        onBlur={(e) => {
                                            if (!e.target.value || parseInt(e.target.value, 10) < 1) {
                                                updateQuantity(item.product.id, 1);
                                            }
                                        }}
                                        onKeyDown={blockNonIntegerKeys}
                                        onPaste={sanitizeIntegerPaste}
                                    />
                                    <button className="qty-btn" onClick={() => updateQuantity(item.product.id, item.quantity + 1)}>+</button>
                                </div>
                                <div className="cart-item-total">
                                    {formatCurrency((Math.max(0, item.unitPrice - (item.discount || 0))) * item.quantity)}
                                </div>
                                <button className="remove-btn" onClick={() => removeFromCart(item.product.id)}>×</button>
                            </div>
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
                    </div>

                    {/* New Payment Input Row — FIXED outside scroll area (Issue #26) */}
                    <div className="payment-row payment-row-new">
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
                            type="text"
                            inputMode="decimal"
                            className="payment-amount"
                            placeholder="$"
                            value={paymentAmount}
                            onChange={(e) => {
                                const val = enforceMoneyFormat(e.target.value);
                                setPaymentAmount(val);
                            }}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter') { handleAddPayment(); return; }
                                blockNonNumericKeys(e);
                            }}
                            onPaste={sanitizeNumericPaste}
                        />
                        <button onClick={handleAddPayment} className="add-payment-btn">+</button>
                    </div>

                    <div className="totals-area">
                        <div className="totals-discount-col">
                            <label className="discount-global-label">Desc. Global</label>
                            <input
                                type="text"
                                inputMode="decimal"
                                value={globalDiscount || ''}
                                onChange={(e) => {
                                    const val = enforceMoneyFormat(e.target.value);
                                    setGlobalDiscount(parseFloat(val) || 0);
                                }}
                                onKeyDown={blockNonNumericKeys}
                                onPaste={sanitizeNumericPaste}
                                placeholder="$0"
                                className="discount-global-input"
                            />
                        </div>
                        <div className="totals-numbers-col">
                            <div className="totals-line">Subtotal: {formatCurrency(totals.subtotal)}</div>
                            <div className="totals-line totals-total">Total: {formatCurrency(totals.total)}</div>
                            <div className={`totals-line ${totals.isOverpaid ? 'totals-excedido' :
                                remaining > 0.01 ? 'totals-falta' : 'totals-cubierto'
                            }`}>
                                {totals.isOverpaid
                                    ? `Excedido: ${formatCurrency(totals.totalPaid - totals.total)}`
                                    : remaining > 0.01
                                        ? `Falta: ${formatCurrency(remaining)}`
                                        : 'Cubierto'}
                            </div>
                        </div>
                    </div>
                    <button
                        className="pay-btn"
                        disabled={cartItems.length === 0 || payments.length === 0 || !clientName.trim() || isSubmitting || totals.isOverpaid}
                        onClick={handlePrePaymentCheck}
                        style={{ opacity: (cartItems.length === 0 || payments.length === 0 || !clientName.trim() || isSubmitting || totals.isOverpaid) ? 0.5 : 1 }}
                    >
                        {isSubmitting ? "PROCESANDO..." : "FINALIZAR"}
                    </button>
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

                {/* Issue #8: Overpayment Modal */}
                {showOverpaidModal && (
                    <ConfirmationModal
                        title="⚠️ Monto Excedido"
                        message={`El monto ingresado excede el total de la venta. Dinero faltante por pagar: ${formatCurrency(overpaidMaxAllowed)}. Corrija el monto del método de pago.`}
                        confirmText={`Usar ${formatCurrency(overpaidMaxAllowed)}`}
                        cancelText="Corregir manualmente"
                        isWarning={true}
                        onConfirm={handleAutoCorrectPayment}
                        onCancel={() => setShowOverpaidModal(false)}
                    />
                )}
            </div>

            {/* Tab switching is now handled by the contextual bottom-nav in AppLayout */}
        </div>
    );
}

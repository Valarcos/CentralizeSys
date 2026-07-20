import { useState, useEffect, useRef } from 'react';
import { useBlocker, useNavigate, useLocation } from 'react-router-dom';
import api from '../services/api';
import { formatCurrency, formatDate } from '../utils/format';
import toast from 'react-hot-toast';
import SalesDetailModal from '../components/SalesDetailModal';
import CancellationModal from '../components/CancellationModal';
import FinalizeConfirmationModal from '../components/FinalizeConfirmationModal';
import { generateDebtorReceipt, generatePendingSaleReceipt } from '../utils/pdfGenerator';
import { blockNonNumericKeys, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';
import './SalesHistoryPage.css'; // Reusing CSS for table responsive cards
import './CobrosYPedidosPage.css'; // Scoped overrides for padding and column widths

export default function CobrosYPedidosPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const [items, setItems] = useState([]);
    const [filterType, setFilterType] = useState('ALL'); // 'ALL' | 'FIADO' | 'PEDIDO'
    const [loading, setLoading] = useState(true);

    // Auto-scroll and highlight logic after editing
    useEffect(() => {
        if (!loading && items.length > 0 && location.state?.highlightedSaleId) {
            const rowId = `sale-row-${location.state.highlightedSaleId}`;
            const rowElement = document.getElementById(rowId);

            if (rowElement) {
                // Small timeout ensures the DOM is fully rendered/painted before scrolling
                setTimeout(() => {
                    rowElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    rowElement.classList.add('highlight-row');

                    // Remove highlight class after animation finishes
                    setTimeout(() => {
                        rowElement.classList.remove('highlight-row');
                    }, 3000);

                    // Clear the state so it doesn't happen again on normal reload/navigation
                    navigate(location.pathname, { replace: true, state: {} });
                }, 100);
            }
        }
    }, [loading, items, location, navigate]);

    // Modals
    const [selectedItem, setSelectedItem] = useState(null);
    const [showPayModal, setShowPayModal] = useState(false);
    const [viewSale, setViewSale] = useState(null);
    const [viewItemInfo, setViewItemInfo] = useState(null);
    const [isLoadingSale, setIsLoadingSale] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Confirmation Modals
    const [itemToFinalize, setItemToFinalize] = useState(null);
    const [showFinalizeModal, setShowFinalizeModal] = useState(false);

    // Multi-Payment State
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [payments, setPayments] = useState([]); // Array of { methodId, methodName, amount, note }
    const [selectedMethodId, setSelectedMethodId] = useState('');
    const [paymentAmount, setPaymentAmount] = useState('');
    const [paymentNote, setPaymentNote] = useState('');

    // Req 4: Ref for the payment amount input — used to auto-focus and select text
    // when a payment method is chosen, making it easy to overwrite the auto-filled value.
    const paymentAmountRef = useRef(null);

    const [itemToCancel, setItemToCancel] = useState(null);

    const blocker = useBlocker(showPayModal && payments.length > 0);
    useEffect(() => {
        if (blocker.state === 'blocked') {
            const leave = window.confirm('⚠️ ¿Desea salir?\n\nTiene pagos sin confirmar que se perderán si abandona esta página.');
            if (leave) {
                blocker.proceed();
            } else {
                blocker.reset();
            }
        }
    }, [blocker]);

    useEffect(() => {
        fetchItems();
        fetchPaymentMethods();
    }, []);

    const fetchItems = async () => {
        try {
            const response = await api.get('/cobros-y-pedidos');
            setItems(response.data);
        } catch (error) {
            console.error(error);
            toast.error("Error al cargar cobros y pedidos");
        } finally {
            setLoading(false);
        }
    };

    const fetchPaymentMethods = async () => {
        try {
            const res = await api.get('/ventas/metodos-pago');
            setPaymentMethods(res.data);
        } catch (error) {
            console.error("Error loading payment methods:", error);
        }
    };

    const handleOpenPayment = (item) => {
        setSelectedItem(item);
        setPayments([]);
        setSelectedMethodId('');
        setPaymentAmount('');
        setPaymentNote('');
        setShowPayModal(true);
    };

    const handleAddPayment = () => {
        if (!selectedMethodId) return toast.error("Seleccione un método de pago");
        if (!paymentAmount || parseFloat(paymentAmount) <= 0) return toast.error("Monto inválido");

        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        const amount = parseFloat(paymentAmount);

        const currentTotal = payments.reduce((sum, p) => sum + p.amount, 0);
        const remaining = selectedItem.saldo_restante - currentTotal;

        if (amount > remaining + 0.01) {
            toast.error(`El monto excede el saldo restante (${formatCurrency(remaining)})`);
            return;
        }

        setPayments([...payments, {
            methodId: method.id,
            methodName: method.descripcion,
            amount: amount,
            observaciones: paymentNote
        }]);

        setSelectedMethodId('');
        setPaymentAmount('');
        setPaymentNote('');
    };

    const handleRemovePayment = (index) => {
        const newPayments = [...payments];
        newPayments.splice(index, 1);
        setPayments(newPayments);
    };

    const handleRegisterPayment = async () => {
        if (payments.length === 0) return toast.error("Agregue al menos un pago");

        setIsSubmitting(true);
        try {
            const payload = payments.map(p => ({
                montoPago: p.amount,
                metodoPagoId: p.methodId,
                observaciones: p.observaciones
            }));

            if (selectedItem.tipo === 'FIADO') {
                await api.post(`/deudores/${selectedItem.id_referencia}/pagar`, payload);
            } else {
                await api.post(`/ventas-pendientes/${selectedItem.id_referencia}/pagos`, payload);
            }

            toast.success("Pago registrado con éxito");
            setShowPayModal(false);
            fetchItems();
        } catch (error) {
            console.error(error);
            toast.error("Error al registrar pago");
        } finally {
            setIsSubmitting(false);
        }
    };

    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
    const remainingDebt = selectedItem ? Math.max(0, selectedItem.saldo_restante - totalPaid) : 0;
    const availableMethods = paymentMethods.filter(m => !payments.some(p => p.methodId === m.id));

    const handleViewDetails = async (item) => {
        setIsLoadingSale(true);
        try {
            let response;
            if (item.tipo === 'FIADO') {
                // For FIADO, id_referencia is debtor id, we need to fetch the debtor to get ventaId
                if (item.venta_id) {
                    response = await api.get(`/ventas/${item.venta_id}`);
                } else {
                    throw new Error("Deuda no encontrada");
                }
            } else {
                // For PEDIDO, we can use the new pending sale endpoint
                response = await api.get(`/ventas-pendientes/${item.id_referencia}`);
            }
            setViewSale(response?.data);
            setViewItemInfo(item);
        } catch (error) {
            toast.error("Error al cargar detalles");
        } finally {
            setIsLoadingSale(false);
        }
    };

    const handlePrintDebtor = async (item) => {
        if (item.tipo !== 'FIADO') {
            toast.error("La impresión de recibo sólo está disponible para deudas (Fiados).");
            return;
        }

        try {
            toast.loading("Generando recibo...", { id: "print-toast" });
            const [saleRes, debtorRes, pagosRes, methodsRes] = await Promise.all([
                api.get(`/ventas/${item.venta_id}`),
                api.get(`/deudores`), // we need the exact debtor object to get original amount, etc.
                api.get(`/deudores/${item.id_referencia}/pagos`),
                paymentMethods.length > 0 ? Promise.resolve({ data: paymentMethods }) : api.get('/ventas/metodos-pago')
            ]);

            const sale = saleRes.data;
            const debtor = debtorRes.data.find(d => d.id === item.id_referencia);
            const pagos = pagosRes.data;
            const methods = methodsRes.data;

            if (!debtor) throw new Error("Deudor no encontrado");

            const enrichedSalePayments = (sale.pagos || []).map(p => {
                const method = methods.find(m => m.id === p.metodoPagoId);
                return { name: method ? method.descripcion : 'Desconocido', amount: p.monto };
            });

            const debtorData = {
                ventaId: item.venta_id,
                clienteNombre: item.cliente_nombre,
                fechaDeuda: debtor.fechaDeuda,
                estado: item.estado,
                montoOriginal: debtor.montoOriginal,
                montoDeuda: debtor.montoDeuda,
                saleDate: sale.fecha,
                user: sale.vendedorNombre || 'Sistema',
                saleType: sale.tipoVenta || 'ESTÁNDAR',
                items: (sale.items || []).map(d => ({
                    codigo: d.productoCodigo || d.codigoSnapshot,
                    descripcion: d.productoNombre || d.descripcionSnapshot,
                    quantity: d.cantidad,
                    unitPrice: d.precioUnitario,
                    discount: d.descuentoValor || 0,
                    subtotal: d.subtotal
                })),
                pagosDeuda: pagos,
                salePayments: enrichedSalePayments,
                globalDiscount: sale.descuentoGlobal || 0
            };

            generateDebtorReceipt(debtorData);
            toast.success("Recibo generado.", { id: "print-toast" });
        } catch (error) {
            console.error('Error generating debtor PDF:', error);
            toast.error('Error al generar el PDF de deuda', { id: "print-toast" });
        }
    };

    const handlePrintPedido = async (item) => {
        try {
            toast.loading("Generando recibo de pedido...", { id: "print-pedido-toast" });
            const [saleRes, pagosRes, methodsRes] = await Promise.all([
                api.get(`/ventas-pendientes/${item.id_referencia}`),
                api.get(`/ventas-pendientes/${item.id_referencia}/pagos`),
                paymentMethods.length > 0 ? Promise.resolve({ data: paymentMethods }) : api.get('/ventas/metodos-pago')
            ]);

            const sale = saleRes.data;
            const pagos = pagosRes.data;
            const methods = methodsRes.data;

            const enrichedPagos = pagos.map(p => {
                const method = methods.find(m => m.id === p.metodoPagoId);
                return { name: method ? method.descripcion : 'Desconocido', amount: p.monto, date: p.fechaPago };
            });

            const pedidoData = {
                id: sale.id,
                clienteNombre: sale.clienteNombre || item.cliente_nombre,
                fechaCreacion: sale.fecha,
                estado: sale.estado,
                montoTotal: sale.totalVenta,
                montoPagado: item.monto_pagado,
                saldoRestante: item.saldo_restante,
                vendedor: sale.vendedorNombre || 'Sistema',
                items: (sale.items || []).map(d => ({
                    codigo: d.productoCodigo || d.codigoSnapshot,
                    descripcion: d.productoNombre || d.descripcionSnapshot,
                    quantity: d.cantidad,
                    unitPrice: d.precioUnitario,
                    discount: d.descuentoValor || 0,
                    subtotal: d.subtotal
                })),
                pagos: enrichedPagos,
                globalDiscount: sale.descuentoGlobal || 0
            };

            generatePendingSaleReceipt(pedidoData);
            toast.success("Recibo de pedido generado.", { id: "print-pedido-toast" });
        } catch (error) {
            console.error('Error generating pedido PDF:', error);
            toast.error('Error al generar el PDF de pedido', { id: "print-pedido-toast" });
        }
    };

    const handleFinalizarPedido = async (item) => {
        if (!window.confirm(`¿Desea finalizar el pedido de ${item.cliente_nombre}? Se registrará como una venta definitiva.`)) {
            return;
        }

        setItemToFinalize(item);
        setShowFinalizeModal(true);
    };

    const confirmFinalizePedido = async () => {
        if (!itemToFinalize) return;
        setIsSubmitting(true);
        try {
            await api.post(`/ventas-pendientes/${itemToFinalize.id_referencia}/finalizar`);
            toast.success("Pedido finalizado con éxito.");
            setShowFinalizeModal(false);
            setItemToFinalize(null);
            fetchItems();
        } catch (error) {
            console.error(error);
            toast.error("Error al finalizar el pedido.");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCancelarPedido = (item) => {
        setItemToCancel(item);
    };

    const confirmCancelPedido = async () => {
        if (!itemToCancel) return;
        setIsSubmitting(true);
        try {
            await api.post(`/ventas-pendientes/${itemToCancel.id_referencia}/cancelar`);
            toast.success("Pedido cancelado exitosamente. Stock retornado.");
            fetchItems();
        } catch (error) {
            console.error(error);
            toast.error("Error al cancelar el pedido.");
        } finally {
            setIsSubmitting(false);
            setItemToCancel(null);
        }
    };

    const handleEditPedido = async (item) => {
        try {
            toast.loading("Cargando pedido...", { id: "edit-toast" });
            const [saleRes, pagosRes] = await Promise.all([
                api.get(`/ventas-pendientes/${item.id_referencia}`),
                api.get(`/ventas-pendientes/${item.id_referencia}/pagos`)
            ]);

            const saleData = saleRes.data;
            saleData.pagos = pagosRes.data;

            toast.dismiss("edit-toast");
            navigate('/ventas', { state: { pendingSaleToEdit: saleData } });
        } catch (error) {
            console.error(error);
            toast.error("Error al cargar el pedido para editar", { id: "edit-toast" });
        }
    };

    const displayedItems = filterType === 'ALL' ? items : items.filter(i => i.tipo === filterType);

    return (
        <div className="history-page container">
            <div className="history-header">
                <h1>Cobros y Pedidos</h1>
            </div>

            {/* Filter Tabs
               ARCHITECTURE NOTE (Req 3c/3d): The UI labels below are intentionally different
               from the backend domain values ('FIADO', 'PEDIDO'). This is a FRONTEND-ONLY rename
               per business requirements. The filterType state, API calls, and DB schema
               must continue using the original domain strings. DO NOT change the onClick values. */}
            <div className="sale-type-toggle" style={{ marginBottom: '1rem', justifyContent: 'center' }}>
                <button
                    className={`toggle-btn ${filterType === 'ALL' ? 'active' : ''}`}
                    onClick={() => setFilterType('ALL')}
                >
                    TODOS
                </button>
                {/* UI label: "Cheque" — Domain value: 'FIADO' (unchanged in state/API/DB) */}
                <button
                    className={`toggle-btn filter-btn-cheque ${filterType === 'FIADO' ? 'active' : ''}`}
                    onClick={() => setFilterType('FIADO')}
                >
                    📋 Cheque
                </button>
                {/* UI label: "Seña" — Domain value: 'PEDIDO' (unchanged in state/API/DB) */}
                <button
                    className={`toggle-btn filter-btn-sena ${filterType === 'PEDIDO' ? 'active' : ''}`}
                    onClick={() => setFilterType('PEDIDO')}
                >
                    📝 Seña
                </button>
            </div>

            {loading ? (
                <p>Cargando...</p>
            ) : (
                <div className="table-responsive">
                    <table className="history-table cobros-table">
                        <thead>
                        <tr>
                            <th>Cliente</th>
                            <th className="col-pendiente-fecha">Fecha</th>
                            <th className="col-pendiente-tipo">Tipo</th>
                            <th className="col-pendiente-venta">Venta</th>
                            <th className="col-pendiente-productos">Productos</th>
                            <th>Costo Total</th>
                            <th>Monto Total</th>
                            <th>Monto Pagado</th>
                            <th>Saldo Restante</th>
                            <th className="col-pendiente-estado">Estado</th>
                            <th>Acciones</th>
                        </tr>
                        </thead>
                        <tbody>
                        {displayedItems.map((item, index) => (
                            <tr key={`${item.tipo}-${item.id_referencia}-${index}`} id={`sale-row-${item.id_referencia}`}>
                                <td data-label="Cliente">{item.cliente_nombre}</td>
                                <td data-label="Fecha">{formatDate(item.fecha_creacion)}</td>
                                <td data-label="Tipo">
                                    {/*
                                      ARCHITECTURE NOTE (Req 3c/3d): UI display labels are intentionally
                                      different from domain values. 'FIADO' displays as 'Cheque' (teal).
                                      'PEDIDO' displays as 'Seña' (red). The `item.tipo` value itself
                                      is never changed — it is used for all API routing decisions.
                                    */}
                                    {item.tipo === 'FIADO' ? (
                                        <span className="status-pill" style={{ backgroundColor: '#ccfbf1', color: '#0d9488', fontWeight: 700 }}>
                                            Cheque
                                        </span>
                                    ) : (
                                        <span className="status-pill" style={{ backgroundColor: '#fee2e2', color: '#b91c1c', fontWeight: 700 }}>
                                            Seña
                                        </span>
                                    )}
                                </td>
                                <td data-label="Venta">
                                    {item.tipo_venta ? (
                                        <span className={`badge ${item.tipo_venta === 'MAYORISTA' ? 'badge-wholesale' : 'badge-retail'}`}>
                                            {item.tipo_venta}
                                        </span>
                                    ) : (
                                        <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>—</span>
                                    )}
                                </td>
                                <td data-label="Productos" className="col-pendiente-productos">
                                    {item.cantidad_productos ?? 0}
                                </td>
                                <td data-label="Costo Total" className="amount-cell">{formatCurrency(item.costo_total)}</td>
                                <td data-label="Monto Total" className="amount-cell">{formatCurrency(item.monto_total)}</td>
                                <td data-label="Monto Pagado" className="amount-cell">{formatCurrency(item.monto_pagado)}</td>
                                <td data-label="Saldo Restante" className="amount-cell" style={{ fontWeight: 'bold', color: item.saldo_restante > 0 ? '#dc2626' : '#16a34a' }}>
                                    {formatCurrency(item.saldo_restante)}
                                </td>
                                <td data-label="Estado">
                                        <span className={`status-pill status-${item.estado?.toLowerCase()}`}>
                                            {item.estado}
                                        </span>
                                </td>
                                <td data-label="Acciones">
                                    <div className="action-buttons">
                                        <div className="action-buttons-row">
                                            {/* 1. Pago */}
                                            {item.saldo_restante > 0 && (
                                                <button className="btn-pay" onClick={() => handleOpenPayment(item)}>
                                                    💰 Pago
                                                </button>
                                            )}

                                            {/* 2. Ver Detalle */}
                                            <button
                                                className="btn-details"
                                                onClick={() => handleViewDetails(item)}
                                                disabled={isLoadingSale}
                                            >
                                                👁️ Ver Detalle
                                            </button>

                                            {/* 3. Imprimir */}
                                            {item.tipo === 'FIADO' && (
                                                <button
                                                    className="btn-print"
                                                    onClick={() => handlePrintDebtor(item)}
                                                >
                                                    🖨️ Imprimir
                                                </button>
                                            )}
                                            {item.tipo === 'PEDIDO' && item.estado === 'PENDIENTE' && (
                                                <button
                                                    className="btn-print"
                                                    onClick={() => handlePrintPedido(item)}
                                                >
                                                    🖨️ Imprimir
                                                </button>
                                            )}
                                            {item.tipo === 'PEDIDO' && item.estado === 'PENDIENTE' && (
                                                <button
                                                    className="btn-pay"
                                                    style={{ backgroundColor: '#f59e0b', border: '1px solid #d97706' }}
                                                    onClick={() => handleEditPedido(item)}
                                                >
                                                    ✏️ Editar
                                                </button>
                                            )}
                                        </div>

                                        {/* Row 2: Finalizar y Cancelar (Solo Pedidos Pendientes) */}
                                        {item.tipo === 'PEDIDO' && item.estado === 'PENDIENTE' && (
                                            <div className="action-buttons-row">
                                                <button
                                                    className="btn-pay"
                                                    style={{ backgroundColor: item.monto_pagado > 0 ? '#2563eb' : '#94a3b8', border: '1px solid ' + (item.monto_pagado > 0 ? '#1d4ed8' : '#64748b') }}
                                                    onClick={() => handleFinalizarPedido(item)}
                                                    disabled={item.monto_pagado === 0 || isSubmitting}
                                                    title={item.monto_pagado === 0 ? "Debe registrar un pago parcial antes de finalizar" : "Finalizar pedido y crear venta"}
                                                >
                                                    {isSubmitting ? 'Procesando...' : '✅ Finalizar Venta'}
                                                </button>
                                                <button className="btn-delete" onClick={() => handleCancelarPedido(item)} disabled={isSubmitting}>
                                                    ❌ Cancelar Pedido
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                        {displayedItems.length === 0 && (
                            <tr><td colSpan="11" style={{ textAlign: 'center' }}>No hay cobros ni pedidos registrados.</td></tr>
                        )}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Modal de Pago */}
            {showPayModal && selectedItem && (
                <div className="modal-overlay">
                    <div className="modal-content" style={{ maxWidth: '600px' }}>
                        <div style={{ backgroundColor: 'var(--color-primary)', color: 'white', padding: '0.8rem 1rem', margin: '-1rem -1rem 0.8rem -1rem', borderRadius: '8px 8px 0 0' }}>
                            <h3 style={{ margin: 0, color: 'white' }}>
                                {/* Req 3c/3d: UI label uses renamed display name; domain value stays in selectedItem.tipo */}
                                Registrar Pago ({selectedItem.tipo === 'FIADO' ? 'Cheque' : 'Seña'})
                            </h3>
                        </div>
                        <p>Cliente: <strong>{selectedItem.cliente_nombre}</strong></p>
                        {/* Req 3c/3d: Display label uses renamed term; routing logic uses original domain value */}
                        <p>Total {selectedItem.tipo === 'FIADO' ? 'Cheque' : 'Seña'}: <strong>${selectedItem.saldo_restante.toFixed(2)}</strong></p>
                        <p>Restante a Pagar: <strong style={{ color: remainingDebt === 0 ? 'green' : 'red' }}>${remainingDebt.toFixed(2)}</strong></p>

                        <div className="payment-form" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
                            <select
                                value={selectedMethodId}
                                onChange={(e) => {
                                    setSelectedMethodId(e.target.value);
                                    if (remainingDebt > 0 && !paymentAmount) setPaymentAmount(remainingDebt.toFixed(2));
                                    // Req 4: Auto-focus and select the amount input when method is chosen
                                    // so the user can immediately type over the auto-filled value.
                                    setTimeout(() => {
                                        paymentAmountRef.current?.focus();
                                        paymentAmountRef.current?.select();
                                    }, 0);
                                }}
                                style={{ padding: '8px' }}
                            >
                                <option value="">Seleccione Método...</option>
                                {availableMethods.map(m => (
                                    <option key={m.id} value={m.id}>{m.descripcion}</option>
                                ))}
                            </select>

                            <input
                                ref={paymentAmountRef}
                                type="text"
                                inputMode="decimal"
                                placeholder="Monto"
                                value={paymentAmount}
                                onChange={(e) => setPaymentAmount(enforceMoneyFormat(e.target.value))}
                                onKeyDown={blockNonNumericKeys}
                                onPaste={sanitizeNumericPaste}
                                onFocus={(e) => e.target.select()}
                                autoFocus
                                style={{ padding: '8px' }}
                            />
                        </div>
                        <input
                            type="text"
                            placeholder="Observaciones (Opcional)"
                            value={paymentNote}
                            onChange={(e) => setPaymentNote(e.target.value)}
                            style={{ width: '100%', marginBottom: '10px', padding: '8px' }}
                        />
                        <button
                            onClick={handleAddPayment}
                            style={{ width: '100%', marginBottom: '15px', backgroundColor: '#6c757d', color: 'white', border: '2px solid #5a6268' }}
                            disabled={!selectedMethodId || !paymentAmount}
                        >
                            ➕ Agregar Pago
                        </button>

                        {payments.length > 0 && (
                            <div style={{ marginBottom: '15px', border: '1px solid #ddd', padding: '10px', borderRadius: '4px' }}>
                                <h5>Pagos a Registrar:</h5>
                                <ul style={{ listStyle: 'none', padding: 0 }}>
                                    {payments.map((p, idx) => (
                                        <li key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', padding: '5px 4px', backgroundColor: idx % 2 === 0 ? '#f8f9fa' : 'white' }}>
                                            <span>{p.methodName} {p.observaciones ? `(${p.observaciones})` : ''} - <strong>${p.amount.toFixed(2)}</strong></span>
                                            <button onClick={() => handleRemovePayment(idx)} style={{ background: 'none', border: 'none', color: 'red', cursor: 'pointer' }}>❌</button>
                                        </li>
                                    ))}
                                </ul>
                                <div style={{ textAlign: 'right', fontWeight: 'bold', marginTop: '10px' }}>Total: ${totalPaid.toFixed(2)}</div>
                            </div>
                        )}

                        <div className="modal-actions">
                            <button className="secondary" onClick={() => setShowPayModal(false)} disabled={isSubmitting}>Cancelar</button>
                            <button className="primary" onClick={handleRegisterPayment} disabled={payments.length === 0 || isSubmitting}>
                                {isSubmitting ? 'Registrando...' : 'Confirmar Pago'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Sales Detail Modal */}
            {viewSale && (
                <SalesDetailModal
                    sale={viewSale}
                    onClose={() => { setViewSale(null); setViewItemInfo(null); }}
                    printMode="unified"
                    debtorInfo={viewItemInfo}
                />
            )}

            <CancellationModal
                isOpen={!!itemToCancel}
                title={`Cancelar Pedido de ${itemToCancel?.cliente_nombre}`}
                message={itemToCancel?.monto_pagado > 0
                    ? "⚠️ ATENCIÓN DEVOLUCIÓN: Este pedido tiene pagos registrados. El stock reservado será devuelto al inventario y deberá reembolsar el dinero al cliente. Esta acción no se puede deshacer."
                    : "⚠️ CANCELADO: El stock reservado será devuelto al inventario. Esta acción no se puede deshacer."}
                onConfirm={confirmCancelPedido}
                onCancel={() => setItemToCancel(null)}
                isSubmitting={isSubmitting}
            />

            <FinalizeConfirmationModal
                isOpen={showFinalizeModal}
                onConfirm={confirmFinalizePedido}
                onCancel={() => {
                    setShowFinalizeModal(false);
                    setItemToFinalize(null);
                }}
                isSubmitting={isSubmitting}
            />
        </div>
    );
}

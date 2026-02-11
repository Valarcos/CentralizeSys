import { useState, useEffect } from 'react';
import api from '../services/api';
import { formatCurrency, formatDate } from '../utils/format';
import toast from 'react-hot-toast';
import SalesDetailModal from '../components/SalesDetailModal';
import './SalesHistoryPage.css'; // Reusing CSS

export default function DebtorsPage() {
    const [debtors, setDebtors] = useState([]);
    const [loading, setLoading] = useState(true);

    // Modals
    const [selectedDebtor, setSelectedDebtor] = useState(null);
    const [showPayModal, setShowPayModal] = useState(false);
    const [viewSale, setViewSale] = useState(null); // Renamed from currentSaleDetails to match JSX
    const [isLoadingSale, setIsLoadingSale] = useState(false); // Added missing state

    // Multi-Payment State
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [payments, setPayments] = useState([]); // Array of { methodId, methodName, amount, note }
    const [selectedMethodId, setSelectedMethodId] = useState('');
    const [paymentAmount, setPaymentAmount] = useState('');
    const [paymentNote, setPaymentNote] = useState('');

    useEffect(() => {
        fetchDebtors();
        fetchPaymentMethods();
    }, []);

    const fetchDebtors = async () => {
        try {
            const response = await api.get('/api/deudores');
            setDebtors(response.data);
        } catch (error) {
            console.error(error);
            toast.error("Error al cargar deudores");
        } finally {
            setLoading(false);
        }
    };

    const fetchPaymentMethods = async () => {
        try {
            const res = await api.get('/api/ventas/metodos-pago');
            setPaymentMethods(res.data);
        } catch (error) {
            console.error("Error loading payment methods:", error);
        }
    };

    const handleOpenPayment = (debtor) => { // Renamed from openPayModal
        setSelectedDebtor(debtor);
        setPayments([]); // Reset payments
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

        // Calculate remaining debt to prevent overpayment (optional but good UX)
        const currentTotal = payments.reduce((sum, p) => sum + p.amount, 0);
        const remaining = selectedDebtor.montoDeuda - currentTotal;

        if (amount > remaining + 0.01) { // 0.01 margin
            toast.error(`El monto excede la deuda restante (${formatCurrency(remaining)})`);
            return;
        }

        setPayments([...payments, {
            methodId: method.id,
            methodName: method.descripcion,
            amount: amount,
            observaciones: paymentNote
        }]);

        // Reset inputs
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

        try {
            // Map to Backend DTO: List<PagoDeudaRequest>
            // DTO: { montoPago, metodoPagoId, observaciones, usuarioId (handled by backend?) }
            // Controller expects List<PagoDeudaRequest>
            const payload = payments.map(p => ({
                montoPago: p.amount,
                metodoPagoId: p.methodId,
                observaciones: p.observaciones
            }));

            await api.post(`/api/deudores/${selectedDebtor.id}/pagar`, payload);

            toast.success("Pago registrado con éxito");
            setShowPayModal(false);
            fetchDebtors();
        } catch (error) {
            console.error(error);
            toast.error("Error al registrar pago");
        }
    };

    // Calculate totals for Modal
    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
    const remainingDebt = selectedDebtor ? Math.max(0, selectedDebtor.montoDeuda - totalPaid) : 0;

    // Smart Dropdown: Filter out used methods (User req: "dropdown lists must be smart... just the ones not already selected")
    const availableMethods = paymentMethods.filter(m => !payments.some(p => p.methodId === m.id));

    // Handle View Details (Existing)
    const handleViewDetails = async (ventaId) => {
        setIsLoadingSale(true);
        try {
            const response = await api.get(`/api/ventas/${ventaId}`);
            setViewSale(response.data);
        } catch (error) {
            toast.error("Error al cargar detalles de la venta");
        } finally {
            setIsLoadingSale(false);
        }
    };

    return (
        <div className="history-page container">
            <div className="history-header">
                <h1>Deudores</h1>
            </div>

            {loading ? (
                <p>Cargando...</p>
            ) : (
                <div className="table-responsive">
                    <table className="history-table">
                        <thead>
                        <tr>
                            <th>Cliente</th>
                            <th>ID Venta</th>
                            <th>Fecha Deuda</th>
                            <th>Monto Original</th>
                            <th>Deuda Actual</th>
                            <th>Último Pago</th>
                            <th>Estado</th>
                            <th>Acciones</th>
                        </tr>
                        </thead>
                        <tbody>
                        {debtors.map(d => (
                            <tr key={d.id}>
                                <td>{d.clienteNombre}</td>
                                <td>#{d.ventaId}</td>
                                <td>{formatDate(d.fechaDeuda)}</td>
                                <td className="amount-cell">{formatCurrency(d.montoOriginal)}</td>
                                <td className="amount-cell" style={{ fontWeight: 'bold', color: '#dc2626' }}>
                                    {formatCurrency(d.montoDeuda)}
                                </td>
                                <td>{d.fechaUltimoPago ? formatDate(d.fechaUltimoPago) : '-'}</td>
                                <td>
                                        <span className={`status-pill status-${d.estado?.toLowerCase()}`}>
                                            {d.estado}
                                        </span>
                                </td>
                                <td>
                                    <div className="action-buttons">
                                        <button
                                            className="btn-details"
                                            onClick={() => handleViewDetails(d.ventaId)}
                                            disabled={isLoadingSale}
                                        >
                                            👁️ Ver Detalle
                                        </button>
                                        {d.montoDeuda > 0 && (
                                            <button className="btn-pay" onClick={() => handleOpenPayment(d)}>
                                                💰 Registrar Pago
                                            </button>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                        {debtors.length === 0 && (
                            <tr><td colSpan="8" style={{ textAlign: 'center' }}>No hay deudores registrados.</td></tr>
                        )}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Modal de Pago */}
            {showPayModal && selectedDebtor && (
                <div className="modal-overlay">
                    <div className="modal-content" style={{ maxWidth: '600px' }}>
                        <h3>Registrar Pago</h3>
                        <p>Cliente: <strong>{selectedDebtor.clienteNombre}</strong></p>
                        <p>Deuda Total: <strong>${selectedDebtor.montoDeuda.toFixed(2)}</strong></p>
                        <p>Restante a Pagar: <strong style={{ color: remainingDebt === 0 ? 'green' : 'red' }}>${remainingDebt.toFixed(2)}</strong></p>

                        <div className="payment-form" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
                            <select
                                value={selectedMethodId}
                                onChange={(e) => {
                                    setSelectedMethodId(e.target.value);
                                    // Auto-fill amount if empty?
                                    if (remainingDebt > 0 && !paymentAmount) setPaymentAmount(remainingDebt.toFixed(2));
                                }}
                                style={{ padding: '8px' }}
                            >
                                <option value="">Seleccione Método...</option>
                                {availableMethods.map(m => (
                                    <option key={m.id} value={m.id}>{m.descripcion}</option>
                                ))}
                            </select>

                            <input
                                type="number"
                                placeholder="Monto"
                                value={paymentAmount}
                                onChange={(e) => setPaymentAmount(e.target.value)}
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
                            style={{ width: '100%', marginBottom: '20px', backgroundColor: '#6c757d' }}
                            disabled={!selectedMethodId || !paymentAmount}
                        >
                            ➕ Agregar Pago
                        </button>

                        {/* Payment Stack List */}
                        {payments.length > 0 && (
                            <div style={{ marginBottom: '20px', border: '1px solid #ddd', padding: '10px', borderRadius: '4px' }}>
                                <h5>Pagos a Registrar:</h5>
                                <ul style={{ listStyle: 'none', padding: 0 }}>
                                    {payments.map((p, idx) => (
                                        <li key={idx} style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #eee', padding: '5px 0' }}>
                                            <span>{p.methodName} {p.observaciones ? `(${p.observaciones})` : ''} - <strong>${p.amount.toFixed(2)}</strong></span>
                                            <button
                                                onClick={() => handleRemovePayment(idx)}
                                                style={{ background: 'none', border: 'none', color: 'red', cursor: 'pointer' }}
                                            >
                                                ❌
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                                <div style={{ textAlign: 'right', fontWeight: 'bold', marginTop: '10px' }}>
                                    Total: ${totalPaid.toFixed(2)}
                                </div>
                            </div>
                        )}

                        <div className="modal-actions">
                            <button onClick={handleRegisterPayment} disabled={payments.length === 0}>Confirmar Pago</button>
                            <button onClick={() => setShowPayModal(false)}>Cancelar</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Sales Detail Modal */}
            {viewSale && (
                <SalesDetailModal
                    sale={viewSale}
                    onClose={() => setViewSale(null)}
                />
            )}
        </div>
    );
}

import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { formatCurrency } from '../utils/format';
import { printVentaReceipt, printDeudaReceipt, printPendingReceipt } from '../services/receiptService';
import toast from 'react-hot-toast';
import '../pages/SalesHistoryPage.css';

export default function SalesDetailModal({ sale, onClose, printMode = 'ticket', debtorInfo = null }) {
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [isPrinting, setIsPrinting] = useState(false);
    const isPrintingRef = useRef(false);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/ventas/metodos-pago');
                if (isMounted.current) setPaymentMethods(res.data);
            } catch (error) {
                console.error("Error fetching payment methods", error);
            }
        };
        fetchPaymentMethods();
        return () => { isMounted.current = false; };
    }, []);

    if (!sale) return null;

    // Debtor ID resolution:
    // If debtorInfo is a DeudaResponse (from SalesHistoryPage), it has .id.
    // If debtorInfo is a unified item from CobrosYPedidosPage, it has .tipo === 'FIADO' and .id_referencia.
    const resolvedDebtorId = debtorInfo?.tipo === 'FIADO'
        ? debtorInfo.id_referencia
        : (printMode === 'debtor' ? (debtorInfo?.id ?? null) : null);

    const isDebtorMode = printMode === 'debtor' || (printMode === 'unified' && debtorInfo?.tipo === 'FIADO');
    const isPendingMode = printMode === 'pending' || sale?.estado === 'PENDIENTE' || (printMode === 'unified' && debtorInfo?.tipo === 'PEDIDO');

    // Shared guard: ref blocks re-entry synchronously, state drives the disabled buttons.
    const runPrint = async (print, errorMsg) => {
        if (isPrintingRef.current) return;
        isPrintingRef.current = true;
        setIsPrinting(true);
        try {
            await print();
        } catch (error) {
            console.error(errorMsg, error);
            toast.error(errorMsg);
        } finally {
            isPrintingRef.current = false;
            if (isMounted.current) setIsPrinting(false);
        }
    };

    const handlePrintTicket = () => runPrint(
        () => printVentaReceipt(sale.id, { methods: paymentMethods }),
        'Error al generar el PDF de la venta'
    );

    const handlePrintPending = () => runPrint(
        () => printPendingReceipt(sale.id, { methods: paymentMethods }),
        'Error al generar el PDF del pedido'
    );

    // Only called when the user clicks the print button in debtor context
    const handlePrintDeuda = () => {
        if (!resolvedDebtorId) return Promise.resolve();
        return runPrint(
            () => printDeudaReceipt(resolvedDebtorId, { methods: paymentMethods }),
            'Error al generar el PDF de deuda'
        );
    };

    return (
        <div className="modal-overlay">
            {/* Class handles width now */}
            <div className="sales-detail-modal-content">
                <div className="modal-header">
                    <h2>Detalle de Venta #{sale.id}</h2>
                    {/* X button removed per request */}
                </div>

                <div className="sale-info">
                    <p><strong>Fecha:</strong> {sale.fecha}</p>
                    <p><strong>Cliente:</strong> {sale.clienteNombre || 'Consumidor Final'}</p>
                    <p><strong>Total:</strong> {formatCurrency(sale.totalVenta)}</p>
                    <p><strong>Cantidad de Productos:</strong> {sale.cantidadProductos || sale.items?.reduce((sum, item) => sum + (item.cantidad || 0), 0) || 0}</p>
                    {sale.descuentoGlobal > 0 && (
                        <p style={{ color: 'green' }}><strong>Descuento Global:</strong> -{formatCurrency(sale.descuentoGlobal)}</p>
                    )}
                    {sale.recargoGlobal > 0 && (
                        <p style={{ color: '#dc2626' }}><strong>Recargo Global:</strong> +{formatCurrency(sale.recargoGlobal)}</p>
                    )}
                </div>

                <div className="table-responsive modal-table-container">
                    <table className="history-table">
                        <thead>
                        <tr>
                            <th>Producto</th>
                            <th>Cant</th>
                            <th>P. Unit</th>
                            <th>Subtotal</th>
                        </tr>
                        </thead>
                        <tbody>
                        {(sale.items || []).map((d, index) => (
                            <tr key={index}>
                                <td data-label="Producto">
                                    <div className="product-name-cell">{d.descripcionSnapshot || d.productoNombre}</div>
                                    <small className="text-muted">{d.codigoSnapshot || d.productoCodigo}</small>
                                </td>
                                <td data-label="Cant">
                                    {d.cantidad}
                                    {d.cantidadDevuelta > 0 && (
                                        <span style={{color: '#dc2626', fontSize: '0.8rem', marginLeft: '4px', fontWeight: 'bold'}}>
                                            (-{d.cantidadDevuelta})
                                        </span>
                                    )}
                                </td>
                                <td data-label="P. Unit">{formatCurrency(d.precioUnitario)}</td>
                                <td data-label="Subtotal">{formatCurrency(d.delineaTotal || d.subtotal)}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                <div className="modal-actions">
                    <button onClick={onClose} className="btn-confirm secondary-action">Cerrar</button>
                    {isDebtorMode ? (
                        <button
                            onClick={handlePrintDeuda}
                            className="btn-print primary-action"
                            disabled={isPrinting}
                        >
                            🖨️ Imprimir Deuda
                        </button>
                    ) : isPendingMode ? (
                        <button
                            onClick={handlePrintPending}
                            className="btn-print primary-action"
                            disabled={isPrinting}
                        >
                            🖨️ Imprimir Pedido
                        </button>
                    ) : (
                        <button
                            onClick={handlePrintTicket}
                            className="btn-print primary-action"
                            disabled={isPrinting}
                        >
                            🖨️ Imprimir Ticket
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

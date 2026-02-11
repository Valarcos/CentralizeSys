import { useState, useEffect } from 'react';
import api from '../services/api'; // Import api
import { formatCurrency } from '../utils/format';
import { generateReceipt } from '../utils/pdfGenerator'; // Import generator
import '../pages/SalesHistoryPage.css';

export default function SalesDetailModal({ sale, onClose }) {
    const [paymentMethods, setPaymentMethods] = useState([]);

    useEffect(() => {
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/api/ventas/metodos-pago');
                setPaymentMethods(res.data);
            } catch (error) {
                console.error("Error fetching payment methods", error);
            }
        };
        fetchPaymentMethods();
    }, []);

    if (!sale) return null;

    const handlePrint = () => {
        if (!sale || !paymentMethods.length) return;

        // Map payments to include names
        const enrichedPayments = (sale.pagos || []).map(p => {
            const method = paymentMethods.find(m => m.id === p.metodoPagoId);
            return {
                name: method ? method.descripcion : 'Desconocido',
                amount: p.monto
            };
        });

        const receiptData = {
            id: sale.id,
            date: sale.fecha,
            client: sale.clienteNombre,
            user: 'Sistema', // Field not strictly in VentaResponse, defaulting

            // VentaResponse from backend now includes tipoVenta (e.g., "MAYORISTA", "MINORISTA")
            // We pass it to the PDF generator to show correct pricing context.
            saleType: sale.tipoVenta || 'ESTÁNDAR',

            items: (sale.items || []).map(d => ({
                codigo: d.productoCodigo || d.codigoSnapshot,
                descripcion: d.productoNombre || d.descripcionSnapshot, // Check field names in DetalleVenta
                quantity: d.cantidad,
                unitPrice: d.precioUnitario,
                discount: d.descuentoValor || 0,
                subtotal: d.subtotal
            })),
            payments: enrichedPayments,
            total: sale.totalVenta,
            globalDiscount: sale.descuentoGlobal || 0
        };

        generateReceipt(receiptData);
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content" style={{ maxWidth: '800px', width: '90%' }}>
                <div className="modal-header">
                    <h2>Detalle de Venta #{sale.id}</h2>
                    <button onClick={onClose} className="close-btn">×</button>
                </div>

                <div className="sale-info">
                    <p><strong>Fecha:</strong> {sale.fecha}</p>
                    <p><strong>Cliente:</strong> {sale.clienteNombre || 'Consumidor Final'}</p>
                    <p><strong>Total:</strong> {formatCurrency(sale.totalVenta)}</p>
                    {sale.descuentoGlobal > 0 && (
                        <p style={{ color: 'green' }}><strong>Descuento Global:</strong> -{formatCurrency(sale.descuentoGlobal)}</p>
                    )}
                </div>

                <div className="table-responsive" style={{ marginTop: '1rem' }}>
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
                                <td>
                                    <div>{d.descripcionSnapshot || d.productoNombre}</div>
                                    <small className="text-muted">{d.codigoSnapshot || d.productoCodigo}</small>
                                </td>
                                <td>{d.cantidad}</td>
                                <td>{formatCurrency(d.precioUnitario)}</td>
                                <td>{formatCurrency(d.subtotal)}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
                    <button
                        onClick={handlePrint}
                        className="btn-print"
                    >
                        🖨️ Imprimir Ticket
                    </button>
                    <button onClick={onClose} className="btn-confirm">Cerrar</button>
                </div>
            </div>
        </div>
    );
}

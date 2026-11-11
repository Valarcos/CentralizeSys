import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useCart from './useCart';

describe('useCart Hook - Core Operations & Pricing', () => {
    const mockProduct = {
        id: 1,
        codigo: 'A1',
        descripcion: 'Product A',
        precioMinorista: 100.0,
        precioMayorista: 80.0,
        cantidadStock: 10
    };

    const mockProduct2 = {
        id: 2,
        codigo: 'B1',
        descripcion: 'Product B',
        precioMinorista: 50.0,
        precioMayorista: 40.0,
        cantidadStock: 5
    };

    it('initializes with empty cart and default sale type (MINORISTA)', () => {
        const { result } = renderHook(() => useCart());
        expect(result.current.cartItems).toEqual([]);
        expect(result.current.saleType).toBe('MINORISTA');
        expect(result.current.totals.subtotal).toBe(0);
        expect(result.current.totals.total).toBe(0);
    });

    it('adds item to cart with default retail price', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct);
        });

        expect(result.current.cartItems).toHaveLength(1);
        expect(result.current.cartItems[0].product.id).toBe(1);
        expect(result.current.cartItems[0].quantity).toBe(1);
        expect(result.current.cartItems[0].unitPrice).toBe(100.0);
    });

    it('increments quantity when existing item is added again', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct);
            result.current.addToCart(mockProduct);
        });

        expect(result.current.cartItems).toHaveLength(1);
        expect(result.current.cartItems[0].quantity).toBe(2);
    });

    it('switches sale type and recalculates prices dynamically', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // Retail 100
        });

        expect(result.current.totals.total).toBe(100.0);

        act(() => {
            result.current.setSaleType('MAYORISTA');
        });

        expect(result.current.saleType).toBe('MAYORISTA');
        expect(result.current.cartItems[0].unitPrice).toBe(80.0); // Wholesale Price
        expect(result.current.totals.total).toBe(80.0);
    });

    it('calculates totals correctly with multiple mixed items', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // 100
            result.current.addToCart(mockProduct2); // 50
        });

        expect(result.current.totals.total).toBe(150.0);
    });

    it('adds payment method to cart state', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // Total 100
            result.current.addPaymentMethod({ methodId: 1, name: 'Efectivo', amount: 100 });
        });

        expect(result.current.payments).toHaveLength(1);
        expect(result.current.payments[0].amount).toBe(100);
    });

    it('validates sale and fails when cart is empty', () => {
        const { result } = renderHook(() => useCart());
        const validation = result.current.validateSale();
        expect(validation.isValid).toBe(false);
        expect(validation.error).toMatch(/No hay productos/);
    });
});

describe('useCart Hook - Cheque & Payment De-duplication Architecture', () => {
    const mockPaymentMethods = [
        { id: 1, descripcion: 'Efectivo', acronimo: 'E', activo: true },
        { id: 2, descripcion: 'Transferencia', acronimo: 'T', activo: true },
        { id: 3, descripcion: 'Cheque', acronimo: 'CH', activo: true }
    ];

    it('maps standard cash payments normally when no linked cheque exists', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 10,
            clienteNombre: 'Cliente Normal',
            clienteId: 5,
            tipoVenta: 'MINORISTA',
            version: 1,
            descuentoGlobal: 0,
            recargoGlobal: 0,
            items: [],
            pagos: [
                { id: 101, metodoPagoId: 1, monto: 500 }
            ],
            cheques: []
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.payments).toHaveLength(1);
        const payment = result.current.payments[0];
        expect(payment.id).toBe(101);
        expect(payment.methodId).toBe(1);
        expect(payment.name).toBe('Efectivo');
        expect(payment.amount).toBe(500);
        expect(payment.isCashedCheque).toBe(false);
        expect(payment.chequeId).toBeNull();
    });

    it('maps pending cheques correctly into payment list', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 11,
            clienteNombre: 'Cliente Con Cheque',
            clienteId: 6,
            tipoVenta: 'MINORISTA',
            version: 1,
            items: [],
            pagos: [],
            cheques: [
                { id: 201, monto: 1200, fechaCobro: '2026-12-01', estado: 'PENDIENTE' }
            ]
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.payments).toHaveLength(1);
        const cheque = result.current.payments[0];
        expect(cheque.id).toBe(201);
        expect(cheque.isCheque).toBe(true);
        expect(cheque.name).toBe('Cheque a Cobrar');
        expect(cheque.amount).toBe(1200);
        expect(cheque.fechaCobro).toBe('2026-12-01');
    });

    it('de-duplicates cashed cheques by enriching the payment as "Cheque (Cobrado: [Metodo])" and marking isCashedCheque', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 12,
            clienteNombre: 'Cliente Cheque Cobrado',
            clienteId: 7,
            tipoVenta: 'MINORISTA',
            version: 1,
            items: [],
            pagos: [
                { id: 102, metodoPagoId: 1, monto: 800 }
            ],
            cheques: [
                { id: 301, monto: 800, fechaCobro: '2026-11-01', estado: 'COBRADO', pagoVentaId: 102 }
            ]
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.payments).toHaveLength(1);
        const enrichedPayment = result.current.payments[0];
        expect(enrichedPayment.id).toBe(102);
        expect(enrichedPayment.methodId).toBe(1);
        expect(enrichedPayment.name).toBe('Cheque (Cobrado: Efectivo)');
        expect(enrichedPayment.amount).toBe(800);
        expect(enrichedPayment.isCashedCheque).toBe(true);
        expect(enrichedPayment.chequeId).toBe(301);
    });

    it('ignores voided (ANULADA) cheques and does not add them to payments', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 13,
            clienteNombre: 'Cliente Cheque Anulado',
            clienteId: 8,
            tipoVenta: 'MINORISTA',
            version: 1,
            items: [],
            pagos: [],
            cheques: [
                { id: 401, monto: 1500, fechaCobro: '2026-10-15', estado: 'ANULADA', pagoVentaId: null }
            ]
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.payments).toHaveLength(0);
    });

    it('handles mixed scenario: 1 cash, 1 cashed cheque, 1 pending cheque, 1 voided cheque without duplicate payment counts', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 14,
            clienteNombre: 'Cliente Mixto',
            clienteId: 9,
            tipoVenta: 'MINORISTA',
            version: 1,
            items: [
                {
                    id: 1,
                    productoId: 10,
                    productoCodigo: 'PROD-1',
                    productoNombre: 'Articulo Test',
                    cantidad: 1,
                    precioLista: 1000,
                    precioUnitario: 1000,
                    descuentoValor: 0
                }
            ],
            pagos: [
                { id: 501, metodoPagoId: 1, monto: 200 },
                { id: 502, metodoPagoId: 2, monto: 300 }
            ],
            cheques: [
                { id: 601, monto: 300, fechaCobro: '2026-11-01', estado: 'COBRADO', pagoVentaId: 502 },
                { id: 602, monto: 500, fechaCobro: '2026-12-15', estado: 'PENDIENTE', pagoVentaId: null },
                { id: 603, monto: 999, fechaCobro: '2026-09-01', estado: 'ANULADA', pagoVentaId: null }
            ]
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.payments).toHaveLength(3);

        const cashPayment = result.current.payments.find(p => p.id === 501);
        expect(cashPayment).toBeDefined();
        expect(cashPayment.name).toBe('Efectivo');
        expect(cashPayment.isCashedCheque).toBe(false);
        expect(cashPayment.amount).toBe(200);

        const cashedChequePayment = result.current.payments.find(p => p.id === 502);
        expect(cashedChequePayment).toBeDefined();
        expect(cashedChequePayment.name).toBe('Cheque (Cobrado: Transferencia)');
        expect(cashedChequePayment.isCashedCheque).toBe(true);
        expect(cashedChequePayment.chequeId).toBe(601);
        expect(cashedChequePayment.amount).toBe(300);

        const pendingChequePayment = result.current.payments.find(p => p.id === 602);
        expect(pendingChequePayment).toBeDefined();
        expect(pendingChequePayment.name).toBe('Cheque a Cobrar');
        expect(pendingChequePayment.isCheque).toBe(true);
        expect(pendingChequePayment.amount).toBe(500);

        const totals = result.current.totals;
        expect(totals.total).toBe(1000);
        expect(totals.totalPaid).toBe(1000);
        expect(totals.isOverpaid).toBe(false);
    });

    it('correctly detects overpayment when total paid exceeds total sale amount', () => {
        const { result } = renderHook(() => useCart());

        const pendingSale = {
            id: 15,
            clienteNombre: 'Cliente Exceso',
            clienteId: 10,
            tipoVenta: 'MINORISTA',
            version: 1,
            items: [
                {
                    id: 1,
                    productoId: 10,
                    cantidad: 1,
                    precioLista: 500,
                    precioUnitario: 500,
                    descuentoValor: 0
                }
            ],
            pagos: [
                { id: 701, metodoPagoId: 1, monto: 600 }
            ],
            cheques: []
        };

        act(() => {
            result.current.loadCartFromPendingSale(pendingSale, mockPaymentMethods);
        });

        expect(result.current.totals.total).toBe(500);
        expect(result.current.totals.totalPaid).toBe(600);
        expect(result.current.totals.isOverpaid).toBe(true);
    });
});

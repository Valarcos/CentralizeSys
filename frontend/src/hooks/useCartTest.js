import { renderHook, act } from '@testing-library/react';
import useCart from './useCart';

describe('useCart Hook', () => {
    // Mock Product Data
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

    test('should initialize with empty cart and default sale type (MINORISTA)', () => {
        const { result } = renderHook(() => useCart());
        expect(result.current.cartItems).toEqual([]);
        expect(result.current.saleType).toBe('MINORISTA');
        expect(result.current.totals.subtotal).toBe(0);
        expect(result.current.totals.total).toBe(0);
    });

    test('should add item to cart', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct);
        });

        expect(result.current.cartItems).toHaveLength(1);
        expect(result.current.cartItems[0].product.id).toBe(1);
        expect(result.current.cartItems[0].quantity).toBe(1);
        // Default Retail Price
        expect(result.current.cartItems[0].unitPrice).toBe(100.0);
    });

    test('should increment quantity if item exists', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct);
            result.current.addToCart(mockProduct);
        });

        expect(result.current.cartItems).toHaveLength(1);
        expect(result.current.cartItems[0].quantity).toBe(2);
    });

    test('should switch sale type and recalculate prices', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // Price 100
        });

        expect(result.current.totals.total).toBe(100.0);

        act(() => {
            result.current.setSaleType('MAYORISTA');
        });

        expect(result.current.saleType).toBe('MAYORISTA');
        expect(result.current.cartItems[0].unitPrice).toBe(80.0); // Wholesale Price
        expect(result.current.totals.total).toBe(80.0);
    });

    test('should calculate totals correctly with mixed items', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // 100
            result.current.addToCart(mockProduct2); // 50
        });

        expect(result.current.totals.total).toBe(150.0);
    });

    test('should add payment method', () => {
        const { result } = renderHook(() => useCart());

        act(() => {
            result.current.addToCart(mockProduct); // Total 100
            result.current.addPaymentMethod({ methodId: 1, name: 'Efectivo', amount: 100 });
        });

        expect(result.current.payments).toHaveLength(1);
        expect(result.current.payments[0].amount).toBe(100);
    });

    test('should validate sale - Fail if no items', () => {
        const { result } = renderHook(() => useCart());
        const validation = result.current.validateSale();
        expect(validation.isValid).toBe(false);
        expect(validation.error).toMatch(/No hay productos/);
    });

    // Additional tests for Stock Warnings/Debt Logic would go here
});

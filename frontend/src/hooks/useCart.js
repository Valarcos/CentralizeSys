import { useState, useMemo, useCallback } from 'react';

export default function useCart() {
    const [cartItems, setCartItems] = useState([]);
    const [clientName, setClientName] = useState('');
    const [payments, setPayments] = useState([]);
    const [saleType, setSaleState] = useState('MINORISTA'); // 'MINORISTA' | 'MAYORISTA'

    const [globalDiscount, setGlobalDiscount] = useState(0);

    const calculateItemPrice = useCallback((product, type) => {
        if (type === 'MAYORISTA') {
            return product.precioMayorista || 0;
        }
        return product.precioMinorista || 0;
    }, []);

    const addToCart = useCallback((product) => {
        setCartItems(prev => {
            const existingIndex = prev.findIndex(item => item.product.id === product.id);
            if (existingIndex >= 0) {
                const newItems = [...prev];
                newItems[existingIndex] = {
                    ...newItems[existingIndex],
                    quantity: newItems[existingIndex].quantity + 1
                };
                return newItems;
            } else {
                return [...prev, {
                    product,
                    quantity: 1,
                    unitPrice: calculateItemPrice(product, saleType),
                    discount: 0 // New: Item Discount Value
                }];
            }
        });
    }, [saleType, calculateItemPrice]);

    const updateQuantity = useCallback((productId, newQuantity) => {
        if (newQuantity < 0) return 'negative_blocked';
        if (newQuantity < 1) return 'zero_blocked';
        setCartItems(prev => prev.map(item =>
            item.product.id === productId ? { ...item, quantity: newQuantity } : item
        ));
        return 'ok';
    }, []);

    const updateItemDiscount = useCallback((productId, discountValue) => {
        setCartItems(prev => prev.map(item =>
            item.product.id === productId ? { ...item, discount: parseFloat(discountValue) || 0 } : item
        ));
    }, []);

    const removeFromCart = useCallback((productId) => {
        setCartItems(prev => prev.filter(item => item.product.id !== productId));
    }, []);

    const setSaleType = useCallback((type) => {
        setSaleState(type);
        setCartItems(prev => prev.map(item => ({
            ...item,
            unitPrice: calculateItemPrice(item.product, type)
            // Keep existing discount? Yes.
        })));
    }, [calculateItemPrice]);

    const addPaymentMethod = useCallback((payment) => {
        setPayments(prev => [...prev, payment]);
    }, []);

    const removePaymentMethod = useCallback((index) => {
        setPayments(prev => prev.filter((_, i) => i !== index));
    }, []);

    const totals = useMemo(() => {
        // 1. Calculate Subtotal (Sum of (Price - Discount) * Qty)?
        // Wait, discount interpretation varies.
        // Usually: (Price * Qty) - Discount? Or (Price - Discount) * Qty?
        // Backend VentaService Logic:
        // Double precioFinal = calculateFinalPrice(precioBase, valorDescuento);
        // Double subtotal = itemReq.getCantidad() * precioFinal;
        // So Discount is PER UNIT.

        const subtotal = cartItems.reduce((sum, item) => {
            const finalUnitPrice = Math.max(0, item.unitPrice - (item.discount || 0));
            return sum + (finalUnitPrice * item.quantity);
        }, 0);

        // 2. Global Discount
        // Backend Logic: Total = Subtotal - GlobalDiscount
        const total = Math.max(0, subtotal - globalDiscount);

        // 3. Payment validation (Issues #7, #8, #9)
        const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
        const isOverpaid = totalPaid > total + 0.01; // Floating point safety

        return {
            subtotal, // This is actually Total before Global Discount
            total,
            globalDiscount,
            totalPaid,
            isOverpaid
        };
    }, [cartItems, globalDiscount, payments]);

    const validateSale = useCallback(() => {
        if (cartItems.length === 0) {
            return { isValid: false, error: 'No hay productos en el carrito.' };
        }
        return { isValid: true };
    }, [cartItems]);

    return {
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
        totals,
        validateSale
    };
}

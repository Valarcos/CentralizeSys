import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import ProductFormModal from '../components/ProductFormModal';
import DeleteProductModal from '../components/DeleteProductModal';
import StockManagementModal from '../components/StockManagementModal';
import './InventarioPage.css';

export default function InventarioPage() {
    const [products, setProducts] = useState([]);
    const [filteredProducts, setFilteredProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [showProductForm, setShowProductForm] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [showStockModal, setShowStockModal] = useState(false);
    const [editingProduct, setEditingProduct] = useState(null);
    const [deletingProduct, setDeletingProduct] = useState(null);
    const [stockProduct, setStockProduct] = useState(null);

    const fetchProducts = useCallback(async (isBackground = false) => {
        try {
            if (!isBackground) {
                setLoading(true);
            }
            const response = await api.get('/api/productos');
            setProducts(response.data);
            // Only update filtered if we are NOT searching, or re-apply filter?
            // Actually, if we are searching, we should re-filter the NEW products list.
            // The useEffect on [searchQuery, products] handles this automatically when 'products' changes!
            // So we just need to update 'products'.

            // However, the initial filteredProducts set here is important for first load.
            // If background refresh happens while searching, we don't want to reset filteredProducts to full list.
            if (!searchQuery) {
                setFilteredProducts(response.data);
            }
        } catch (error) {
            console.error('Error fetching products:', error);
            // Don't show toast on background refresh error to avoid annoying user
            if (!isBackground) {
                toast.error('Error al cargar productos');
            }
        } finally {
            if (!isBackground) {
                setLoading(false);
            }
        }
    }, [searchQuery]);

    // Initial Load
    useEffect(() => {
        fetchProducts(false);
    }, [fetchProducts]);

    // Auto-refresh interval (every 30 seconds)
    useEffect(() => {
        const intervalId = setInterval(() => {
            fetchProducts(true); // isBackground = true
        }, 30000);

        // Cleanup on unmount (when user leaves the module)
        return () => clearInterval(intervalId);
    }, [fetchProducts]);

    // Filter products based on search query
    useEffect(() => {
        if (!searchQuery.trim()) {
            setFilteredProducts(products);
            return;
        }

        const query = searchQuery.toLowerCase();
        const filtered = products.filter(product =>
            product.descripcion.toLowerCase().includes(query) ||
            (product.codigo && product.codigo.toLowerCase().includes(query))
        );
        setFilteredProducts(filtered);
    }, [searchQuery, products]);

    const handleCreateProduct = () => {
        setEditingProduct(null);
        setShowProductForm(true);
    };

    const handleEditProduct = (product) => {
        setEditingProduct(product);
        setShowProductForm(true);
    };

    const handleDeleteClick = (product) => {
        setDeletingProduct(product);
        setShowDeleteModal(true);
    };

    const handleStockClick = (product) => {
        setStockProduct(product);
        setShowStockModal(true);
    };

    const handleStockSuccess = () => {
        // Refresh products to update stock numbers in the table
        fetchProducts();
    };

    const handleProductFormSuccess = () => {
        setShowProductForm(false);
        setEditingProduct(null);
        fetchProducts();
    };

    const handleDeleteConfirm = async () => {
        if (!deletingProduct) return;

        try {
            await api.delete(`/api/productos/${deletingProduct.id}`);
            toast.success('Producto eliminado correctamente');
            setShowDeleteModal(false);
            setDeletingProduct(null);
            fetchProducts();
        } catch (error) {
            console.error('Error deleting product:', error);
            const message = error.response?.data?.message || 'Error al eliminar producto';
            toast.error(message);
        }
    };

    const getStockClass = (product) => {
        const stock = product.cantidadStock || 0;
        if (stock < 0) return 'stock-badge negative-stock';
        if (stock === 0) return 'stock-badge out-of-stock';
        if (stock <= 5) return 'stock-badge low-stock';
        return 'stock-badge in-stock';
    };

    const formatCurrency = (amount) => {
        if (amount == null) return '-';
        return new Intl.NumberFormat('es-AR', {
            style: 'currency',
            currency: 'ARS'
        }).format(amount);
    };

    return (
        <div className="inventario-page container">
            <header className="inventario-header">
                <h1>📦 Gestión de Inventario</h1>
                <button
                    onClick={handleCreateProduct}
                    className="primary"
                    aria-label="Agregar nuevo producto"
                >
                    + Agregar Producto
                </button>
            </header>

            {/* Search Bar */}
            <div className="search-container">
                <label htmlFor="search-input" className="visually-hidden">
                    Buscar producto
                </label>
                <input
                    id="search-input"
                    type="text"
                    placeholder="🔍 Buscar por descripción o código de artículo..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="search-input"
                    aria-label="Buscar productos"
                />
                {searchQuery && (
                    <button
                        onClick={() => setSearchQuery('')}
                        className="clear-search"
                        aria-label="Limpiar búsqueda"
                    >
                        ✕
                    </button>
                )}
            </div>

            {/* Results Info */}
            <p className="results-info" aria-live="polite">
                {loading ? 'Cargando...' :
                    `Mostrando ${filteredProducts.length} de ${products.length} productos`}
            </p>

            {loading ? (
                <div className="loading-state">
                    <p>Cargando productos...</p>
                </div>
            ) : filteredProducts.length === 0 ? (
                <div className="empty-state">
                    <p>{searchQuery ? 'No se encontraron productos' : 'No hay productos registrados'}</p>
                </div>
            ) : (
                <div className="products-table-container">
                    <table className="products-table" aria-label="Lista de productos">
                        <thead>
                        <tr>
                            <th scope="col">Código</th>
                            <th scope="col">Descripción</th>
                            <th scope="col">Costo</th>
                            <th scope="col">Mayorista</th>
                            <th scope="col">Minorista</th>
                            <th scope="col">Stock</th>
                            <th scope="col">Acciones</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filteredProducts.map((product) => (
                            <tr key={product.id}>
                                <td>{product.codigo || '-'}</td>
                                <td className="product-name-cell">{product.descripcion}</td>
                                <td>{formatCurrency(product.precioCosto)}</td>
                                <td>{formatCurrency(product.precioMayorista)}</td>
                                <td className="price-retail">{formatCurrency(product.precioMinorista)}</td>
                                <td>
                                        <span className={getStockClass(product)}>
                                            {product.cantidadStock} unidades
                                        </span>
                                </td>
                                <td className="actions-cell">
                                    <button
                                        onClick={() => handleStockClick(product)}
                                        className="action-btn stock"
                                        aria-label={`Ajustar stock de ${product.descripcion}`}
                                    >
                                        📦 Stock
                                    </button>
                                    <button
                                        onClick={() => handleEditProduct(product)}
                                        className="action-btn edit"
                                        aria-label={`Editar ${product.descripcion}`}
                                    >
                                        ✏️ Editar
                                    </button>
                                    <button
                                        onClick={() => handleDeleteClick(product)}
                                        className="action-btn delete"
                                        aria-label={`Eliminar ${product.descripcion}`}
                                    >
                                        🗑️ Eliminar
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {showProductForm && (
                <ProductFormModal
                    product={editingProduct}
                    onSuccess={handleProductFormSuccess}
                    onCancel={() => {
                        setShowProductForm(false);
                        setEditingProduct(null);
                    }}
                />
            )}

            {showDeleteModal && deletingProduct && (
                <DeleteProductModal
                    product={deletingProduct}
                    onConfirm={handleDeleteConfirm}
                    onCancel={() => {
                        setShowDeleteModal(false);
                        setDeletingProduct(null);
                    }}
                />
            )}

            {showStockModal && stockProduct && (
                <StockManagementModal
                    product={stockProduct}
                    onClose={() => {
                        setShowStockModal(false);
                        setStockProduct(null);
                    }}
                    onSuccess={handleStockSuccess}
                />
            )}
        </div>
    );
}

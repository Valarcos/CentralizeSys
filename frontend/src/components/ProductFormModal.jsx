import { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import './ProductFormModal.css';

/**
 * Modal for creating/editing products.
 * For new products, includes location selection and quantity input.
 */
export default function ProductFormModal({ product, onSuccess, onCancel }) {
    const isEditing = !!product;
    const firstInputRef = useRef(null);

    // Ubicaciones (locations) for dropdown
    const [ubicaciones, setUbicaciones] = useState([]);
    const [loadingUbicaciones, setLoadingUbicaciones] = useState(true);

    // Form state
    const [formData, setFormData] = useState({
        codigo: product?.codigo || '',
        descripcion: product?.descripcion || '',
        precioCosto: product?.precioCosto || '',
        precioMayorista: product?.precioMayorista || '',
        precioMinorista: product?.precioMinorista || '',
        ubicacionId: '',
        cantidad: ''
    });

    const [errors, setErrors] = useState({});
    const [saving, setSaving] = useState(false);

    // Load ubicaciones on mount (only for new products)
    useEffect(() => {
        if (!isEditing) {
            loadUbicaciones();
        } else {
            setLoadingUbicaciones(false);
        }
    }, [isEditing]);

    // Focus first input on mount
    useEffect(() => {
        firstInputRef.current?.focus();
    }, []);

    const loadUbicaciones = async () => {
        try {
            const response = await api.get('/api/locations');
            setUbicaciones(response.data);
            // Auto-select first location if only one exists
            if (response.data.length === 1) {
                setFormData(prev => ({ ...prev, ubicacionId: response.data[0].id.toString() }));
            }
        } catch (error) {
            console.error('Error loading ubicaciones:', error);
            if (!isEditing) toast.error('Error al cargar ubicaciones');
        } finally {
            setLoadingUbicaciones(false);
        }
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));

        // Clear error when field is edited
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: null }));
        }
    };

    const validate = () => {
        const newErrors = {};

        // Codigo is required
        if (!formData.codigo.trim()) {
            newErrors.codigo = 'Código de artículo obligatorio. Usar "1" para productos sin código.';
        }

        // Descripcion is required
        if (!formData.descripcion.trim()) {
            newErrors.descripcion = 'Descripción obligatoria.';
        }

        // Precio Costo is required and >= 0
        const costo = parseFloat(formData.precioCosto);
        if (isNaN(costo) || costo < 0) {
            newErrors.precioCosto = 'Costo debe ser mayor o igual a 0.';
        }

        // Precio Minorista is required and >= costo
        const minorista = parseFloat(formData.precioMinorista);
        if (isNaN(minorista) || minorista < 0) {
            newErrors.precioMinorista = 'Precio minorista debe ser mayor o igual a 0.';
        } else if (!isNaN(costo) && minorista < costo) {
            newErrors.precioMinorista = 'Precio minorista debe ser mayor o igual al costo.';
        }

        // Precio Mayorista (optional) - if provided, >= costo
        if (formData.precioMayorista) {
            const mayorista = parseFloat(formData.precioMayorista);
            if (isNaN(mayorista) || mayorista < 0) {
                newErrors.precioMayorista = 'Precio mayorista debe ser mayor o igual a 0.';
            } else if (!isNaN(costo) && mayorista < costo) {
                newErrors.precioMayorista = 'Precio mayorista debe ser mayor o igual al costo.';
            }
        }

        // For new products: ubicacion and cantidad are required
        if (!isEditing) {
            if (!formData.ubicacionId) {
                newErrors.ubicacionId = 'Debe seleccionar una ubicación.';
            }

            const cantidad = parseInt(formData.cantidad);
            if (isNaN(cantidad) || cantidad < 1) {
                newErrors.cantidad = 'Cantidad debe ser al menos 1.';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!validate()) {
            toast.error('Por favor, corrija los errores del formulario.');
            return;
        }

        setSaving(true);

        try {
            const payload = {
                codigo: formData.codigo.trim(),
                descripcion: formData.descripcion.trim(),
                precioCosto: parseFloat(formData.precioCosto),
                precioMayorista: formData.precioMayorista ? parseFloat(formData.precioMayorista) : null,
                precioMinorista: parseFloat(formData.precioMinorista)
            };

            // Add stock info for new products
            if (!isEditing && formData.ubicacionId && formData.cantidad) {
                payload.ubicacionId = parseInt(formData.ubicacionId);
                payload.cantidad = parseInt(formData.cantidad);
            }

            if (isEditing) {
                await api.put(`/api/productos/${product.id}`, payload);
                toast.success('Producto actualizado correctamente');
            } else {
                await api.post('/api/productos', payload);
                toast.success('Producto creado correctamente');
            }

            onSuccess();
        } catch (error) {
            console.error('Error saving product:', error);
            const message = error.response?.data?.message || 'Error al guardar producto';
            toast.error(message);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onCancel} role="dialog" aria-modal="true">
            <div className="product-form-modal" onClick={e => e.stopPropagation()}>
                <form onSubmit={handleSubmit}>
                    <div className="modal-header">
                        <h2>{isEditing ? 'Editar Producto' : 'Nuevo Producto'}</h2>
                    </div>

                    <div className="form-body">
                        {/* Codigo */}
                        <div className="form-group">
                            <label htmlFor="codigo">
                                Código de Artículo <span className="required">*</span>
                            </label>
                            <input
                                ref={firstInputRef}
                                id="codigo"
                                name="codigo"
                                type="text"
                                value={formData.codigo}
                                onChange={handleChange}
                                placeholder="Ej: ART-001"
                                aria-invalid={!!errors.codigo}
                            />
                            {errors.codigo && (
                                <span className="error-message">{errors.codigo}</span>
                            )}
                        </div>

                        {/* Descripcion */}
                        <div className="form-group">
                            <label htmlFor="descripcion">
                                Descripción <span className="required">*</span>
                            </label>
                            <input
                                id="descripcion"
                                name="descripcion"
                                type="text"
                                value={formData.descripcion}
                                onChange={handleChange}
                                placeholder="Nombre del producto"
                                aria-invalid={!!errors.descripcion}
                            />
                            {errors.descripcion && (
                                <span className="error-message">{errors.descripcion}</span>
                            )}
                        </div>

                        {/* Precios Row */}
                        <div className="form-row prices-row">
                            <div className="form-group">
                                <label htmlFor="precioCosto">
                                    Costo <span className="required">*</span>
                                </label>
                                <input
                                    id="precioCosto"
                                    name="precioCosto"
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={formData.precioCosto}
                                    onChange={handleChange}
                                    placeholder="0.00"
                                    aria-invalid={!!errors.precioCosto}
                                />
                                {errors.precioCosto && (
                                    <span className="error-message">{errors.precioCosto}</span>
                                )}
                            </div>

                            <div className="form-group">
                                <label htmlFor="precioMayorista">
                                    Precio Mayorista
                                </label>
                                <input
                                    id="precioMayorista"
                                    name="precioMayorista"
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={formData.precioMayorista}
                                    onChange={handleChange}
                                    placeholder="Igual al minorista si vacío"
                                />
                                {errors.precioMayorista && (
                                    <span className="error-message">{errors.precioMayorista}</span>
                                )}
                            </div>

                            <div className="form-group">
                                <label htmlFor="precioMinorista">
                                    Precio Minorista <span className="required">*</span>
                                </label>
                                <input
                                    id="precioMinorista"
                                    name="precioMinorista"
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={formData.precioMinorista}
                                    onChange={handleChange}
                                    placeholder="0.00"
                                    aria-invalid={!!errors.precioMinorista}
                                />
                                {errors.precioMinorista && (
                                    <span className="error-message">{errors.precioMinorista}</span>
                                )}
                            </div>
                        </div>

                        {/* Stock Section - Only for new products */}
                        {!isEditing && (
                            <div className="stock-section">
                                <h3>📍 Ubicación del Stock Inicial</h3>

                                <div className="form-row stock-row">
                                    <div className="form-group">
                                        <label htmlFor="ubicacionId">
                                            Ubicación <span className="required">*</span>
                                        </label>
                                        {loadingUbicaciones ? (
                                            <p className="loading-text">Cargando ubicaciones...</p>
                                        ) : ubicaciones.length === 0 ? (
                                            <p className="warning-text">
                                                No hay ubicaciones registradas.
                                                Contacte al administrador para agregar ubicaciones.
                                            </p>
                                        ) : (
                                            <select
                                                id="ubicacionId"
                                                name="ubicacionId"
                                                value={formData.ubicacionId}
                                                onChange={handleChange}
                                                aria-invalid={!!errors.ubicacionId}
                                            >
                                                <option value="">-- Seleccione ubicación --</option>
                                                {ubicaciones.map(ub => (
                                                    <option key={ub.id} value={ub.id}>
                                                        {ub.nombre}
                                                    </option>
                                                ))}
                                            </select>
                                        )}
                                        {errors.ubicacionId && (
                                            <span className="error-message">{errors.ubicacionId}</span>
                                        )}
                                    </div>

                                    <div className="form-group">
                                        <label htmlFor="cantidad">
                                            Cantidad <span className="required">*</span>
                                        </label>
                                        <input
                                            id="cantidad"
                                            name="cantidad"
                                            type="number"
                                            min="1"
                                            step="1"
                                            value={formData.cantidad}
                                            onChange={handleChange}
                                            placeholder="Ingrese cantidad"
                                            aria-invalid={!!errors.cantidad}
                                        />
                                        {errors.cantidad && (
                                            <span className="error-message">{errors.cantidad}</span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="form-actions">
                        <button
                            type="button"
                            onClick={onCancel}
                            className="secondary"
                            disabled={saving}
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            className="primary"
                            disabled={saving || (loadingUbicaciones && !isEditing)}
                        >
                            {saving ? 'Guardando...' : (isEditing ? 'Actualizar' : 'Crear Producto')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

let cart = [];
let allProducts = [];

// --- INIT ---
document.addEventListener("DOMContentLoaded", () => {
    loadProducts();
    loadPaymentMethods();
});

function showTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.getElementById(tabId).classList.add('active');
    if(tabId === 'historial') loadHistory();
    if(tabId === 'productos') loadProducts();
}

// --- LOAD DATA ---
async function loadProducts() {
    try {
        const res = await fetch('/api/productos');
        allProducts = await res.json();
        renderProducts(allProducts);
        renderInventory(allProducts);
    } catch (e) { console.error("Error loading products", e); }
}

async function loadPaymentMethods() {
    try {
        const res = await fetch('/api/ventas/metodos-pago');
        const methods = await res.json();
        const select = document.getElementById('payment-method');
        select.innerHTML = methods.map(m => `<option value="${m.id}">${m.descripcion}</option>`).join('');
    } catch (e) { console.error("Error methods", e); }
}

async function loadHistory() {
    try {
        const res = await fetch('/api/ventas');
        const sales = await res.json();
        const tbody = document.querySelector('#history-table tbody');
        tbody.innerHTML = sales.map(s => `
            <tr>
                <td>${s.id}</td>
                <td>${s.fecha}</td>
                <td>${s.clienteNombre || 'Anónimo'}</td>
                <td>$${s.totalVenta.toFixed(2)}</td>
                <td><button onclick="alert('Detalle no implementado en demo')">Ver</button></td>
            </tr>
        `).join('');
    } catch (e) { console.error("Error history", e); }
}

// --- RENDER ---
function renderProducts(list) {
    const container = document.getElementById('product-list');
    container.innerHTML = list.map(p => `
        <div>
            <span><b>${p.codigo}</b><br>${p.descripcion}</span>
            <span>$${p.precioMinorista} 
                <button onclick="addToCart(${p.id}, '${p.descripcion}', ${p.precioMinorista})">+</button>
            </span>
        </div>
    `).join('');
}

function renderInventory(list) {
    const tbody = document.querySelector('#inventory-table tbody');
    tbody.innerHTML = list.map(p => `
        <tr>
            <td>${p.codigo}</td>
            <td>${p.descripcion}</td>
            <td>$${p.precioMinorista}</td>
            <td style="font-weight:bold; color:${p.cantidadStock < 2 ? 'red' : 'green'}">${p.cantidadStock}</td>
        </tr>
    `).join('');
}

function filterProducts() {
    const term = document.getElementById('search-box').value.toLowerCase();
    const filtered = allProducts.filter(p =>
        p.descripcion.toLowerCase().includes(term) ||
        p.codigo.toLowerCase().includes(term)
    );
    renderProducts(filtered);
}

// --- CART LOGIC ---
function addToCart(id, desc, price) {
    const existing = cart.find(i => i.productoId === id);
    if (existing) {
        existing.cantidad++;
    } else {
        cart.push({ productoId: id, descripcion: desc, precio: price, cantidad: 1 });
    }
    renderCart();
}

function renderCart() {
    const container = document.getElementById('cart-items');
    let total = 0;
    container.innerHTML = cart.map(item => {
        const subtotal = item.precio * item.cantidad;
        total += subtotal;
        return `
            <div class="cart-row">
                <span>${item.cantidad}x ${item.descripcion.substring(0, 15)}...</span>
                <span>$${subtotal.toFixed(2)}</span>
            </div>`;
    }).join('');
    document.getElementById('total-amount').innerText = total.toFixed(2);
}

// --- ACTIONS ---
async function procesarVenta() {
    if (cart.length === 0) return alert("El carrito está vacío");

    const cliente = document.getElementById('cliente-nombre').value;
    const metodoId = document.getElementById('payment-method').value;
    const total = parseFloat(document.getElementById('total-amount').innerText);

    const payload = {
        clienteNombre: cliente,
        items: cart.map(i => ({
            productoId: i.productoId,
            cantidad: i.cantidad,
            tipoDescuento: 'NONE',
            valorDescuento: 0
        })),
        pagos: [
            { metodoPagoId: parseInt(metodoId), monto: total }
        ]
    };

    try {
        const res = await fetch('/api/ventas', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            alert("Venta Registrada con Éxito!");
            cart = [];
            document.getElementById('cliente-nombre').value = "";
            renderCart();
            loadProducts(); // Refresh stock
        } else {
            const err = await res.json();
            alert("Error: " + err.message); // Will show stock alerts if any
        }
    } catch (e) {
        alert("Error de conexión");
    }
}

async function mockImport() {
    const fileInput = document.getElementById('excel-file');
    if (!fileInput.files[0]) return alert("Seleccione un archivo primero");

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    document.getElementById('import-status').innerText = "Procesando...";

    try {
        const res = await fetch('/api/import/excel', { method: 'POST', body: formData });
        const data = await res.json();
        document.getElementById('import-status').innerText = data.message;
        document.getElementById('import-status').style.color = "green";
        // Reload products just in case (even though it's fake, if you manually edit data.sql later it helps)
        loadProducts();
    } catch (e) {
        document.getElementById('import-status').innerText = "Error en importación";
    }
}
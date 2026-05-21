// app.js - E-Commerce Microservices Control Logic
const GATEWAY_URL = 'http://localhost:8080';
const S3_URL = '/api/s3-invoices';

// App State
let appState = {
    token: localStorage.getItem('jwt_token') || null,
    username: localStorage.getItem('jwt_username') || null,
    role: localStorage.getItem('jwt_role') || null,
    seededProducts: ['PROD-100', 'PROD-200'] // default demo list
};

// UI Console logger
function log(msg, type = 'info') {
    const consolePanel = document.getElementById('console-log-panel');
    if (!consolePanel) return;

    const time = new Date().toLocaleTimeString();
    const line = document.createElement('div');
    line.className = `console-line ${type}-line`;
    line.innerText = `[${time}] ${msg}`;
    
    consolePanel.appendChild(line);
    consolePanel.scrollTop = consolePanel.scrollHeight;
}

function clearLogs() {
    const consolePanel = document.getElementById('console-log-panel');
    if (consolePanel) {
        consolePanel.innerHTML = '<div class="console-line system-line">[SYSTEM] Console cleared. Ready.</div>';
    }
}

// Tab switcher for authentication
function switchAuthTab(type) {
    const loginBtn = document.querySelector('.card-tabs button:nth-child(1)');
    const regBtn = document.querySelector('.card-tabs button:nth-child(2)');
    const loginForm = document.getElementById('login-form-container');
    const regForm = document.getElementById('register-form-container');

    if (type === 'login') {
        loginBtn.classList.add('active');
        regBtn.classList.remove('active');
        loginForm.classList.remove('hidden');
        regForm.classList.add('hidden');
    } else {
        loginBtn.classList.remove('active');
        regBtn.classList.add('active');
        loginForm.classList.add('hidden');
        regForm.classList.remove('hidden');
    }
}

// Initialize Auth View
function initAuthView() {
    const authStatus = document.getElementById('auth-status');
    const authMeta = document.getElementById('auth-meta');
    const metaUser = document.getElementById('meta-username');
    const metaRole = document.getElementById('meta-role');
    const metaToken = document.getElementById('meta-token');
    
    const loginForm = document.getElementById('login-form-container');
    const regForm = document.getElementById('register-form-container');
    const tabs = document.querySelector('.card-tabs');

    if (appState.token) {
        authStatus.innerText = 'Authenticated';
        authStatus.className = 'badge badge-active';
        authMeta.classList.remove('hidden');
        metaUser.innerText = appState.username;
        metaRole.innerText = appState.role;
        metaToken.innerText = appState.token;
        
        loginForm.classList.add('hidden');
        regForm.classList.add('hidden');
        tabs.classList.add('hidden');
        log(`System initialized with existing token for user '${appState.username}'`, 'success');
        
        // Auto fetch orders
        fetchOrders();
    } else {
        authStatus.innerText = 'Not Logged In';
        authStatus.className = 'badge badge-inactive';
        authMeta.classList.add('hidden');
        tabs.classList.remove('hidden');
        switchAuthTab('login');
    }
}

// Log out user
function handleLogout() {
    appState.token = null;
    appState.username = null;
    appState.role = null;
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('jwt_username');
    localStorage.removeItem('jwt_role');
    
    initAuthView();
    fetchOrders(); // Refresh table to show unauth message
    log("Logged out successfully.", "system");
}

// Register Request
async function handleRegister() {
    const username = document.getElementById('reg-username').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value.trim();

    if (!username || !email || !password) {
        log("Registration failed: Fill all fields", "error");
        return;
    }

    log(`POST -> /api/auth/register for username: ${username}...`, 'network');
    
    try {
        const res = await fetch(`${GATEWAY_URL}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, email })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Registration failed');
        }

        const data = await res.json();
        log(`Registration succeeded! Username: ${data.username}. You can now login.`, 'success');
        switchAuthTab('login');
        document.getElementById('login-username').value = username;
        document.getElementById('login-password').value = password;
    } catch (e) {
        log(`Registration failed: ${e.message}`, 'error');
    }
}

// Login Request
async function handleLogin() {
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value.trim();

    if (!username || !password) {
        log("Login failed: Missing username or password", "error");
        return;
    }

    log(`POST -> /api/auth/login for username: ${username}...`, 'network');

    try {
        const res = await fetch(`${GATEWAY_URL}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!res.ok) {
            throw new Error('Invalid credentials or Auth server unavailable.');
        }

        const data = await res.json();
        appState.token = data.token;
        appState.username = data.username;
        appState.role = data.role;

        localStorage.setItem('jwt_token', data.token);
        localStorage.setItem('jwt_username', data.username);
        localStorage.setItem('jwt_role', data.role);

        log(`Login successful! Received JWT for '${data.username}' with role [${data.role}]`, 'success');
        
        initAuthView();
    } catch (e) {
        log(`Login failed: ${e.message}`, 'error');
    }
}

// Seed product details to Stock Service
async function handleSeedProduct() {
    const id = document.getElementById('prod-id').value.trim();
    const name = document.getElementById('prod-name').value.trim();
    const price = parseFloat(document.getElementById('prod-price').value);
    const quantity = parseInt(document.getElementById('prod-qty').value);

    if (!id || !name || isNaN(price) || isNaN(quantity)) {
        log("Seeding failed: Check input fields", "error");
        return;
    }

    log(`POST -> /api/stock/products to seed product ${id}...`, 'network');

    try {
        const res = await fetch(`${GATEWAY_URL}/api/stock/products`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id, name, price, quantity })
        });

        if (!res.ok) {
            throw new Error('Failed to seed catalog product.');
        }

        const data = await res.json();
        log(`Product ${data.id} (${data.name}) seeded successfully. Qty: ${data.quantity}`, 'success');
        
        // Add to active product listing set
        if (!appState.seededProducts.includes(id)) {
            appState.seededProducts.push(id);
        }
        
        // Refresh product list
        fetchProducts();
    } catch (e) {
        log(`Seeding failed: ${e.message}`, 'error');
    }
}

// Query products and display latency (Visualizing Cache-Aside Cache)
async function fetchProducts() {
    const tableBody = document.getElementById('products-table-body');
    if (!tableBody) return;

    if (appState.seededProducts.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center">No products to display.</td></tr>';
        return;
    }

    tableBody.innerHTML = '';
    log("GET -> /api/stock/products/{id} for seeded list...", 'network');

    for (const prodId of appState.seededProducts) {
        const startTime = performance.now();
        
        try {
            const res = await fetch(`${GATEWAY_URL}/api/stock/products/${prodId}`);
            const duration = (performance.now() - startTime).toFixed(1);
            
            if (!res.ok) {
                // If product is deleted or not found
                continue;
            }
            
            const prod = await res.json();
            
            // Deduce caching based on latency threshold (normally DB fetches take ~15-200ms, Redis cache takes <10ms)
            const isCached = duration < 12; 
            const latencyClass = isCached ? 'latency-cached' : 'latency-db';
            const latencyLabel = isCached ? `${duration} ms (Cached)` : `${duration} ms (Database)`;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><code>${prod.id}</code></td>
                <td><strong>${prod.name}</strong></td>
                <td>$${prod.price.toFixed(2)}</td>
                <td>${prod.quantity} units</td>
                <td class="${latencyClass}">${latencyLabel}</td>
            `;
            tableBody.appendChild(row);
        } catch (e) {
            log(`Failed to fetch product ${prodId}: ${e.message}`, 'error');
        }
    }

    if (tableBody.children.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center">Products failed to render.</td></tr>';
    }
}

// Submit Checkout Order (Triggers Saga orchestration in backend)
async function handleCheckout() {
    if (!appState.token) {
        log("Checkout block: You must log in to obtain JWT authentication token first!", "warning");
        alert("Please Login or Register first.");
        return;
    }

    const prodId = document.getElementById('check-prod-id').value.trim();
    const quantity = parseInt(document.getElementById('check-prod-qty').value);
    const cardNumber = document.getElementById('card-number').value.trim();
    const expiryDate = document.getElementById('card-expiry').value.trim();
    const cvv = document.getElementById('card-cvv').value.trim();

    if (!prodId || isNaN(quantity) || quantity < 1 || !cardNumber || !expiryDate || !cvv) {
        log("Checkout failed: Missing checkout items or card information.", "error");
        return;
    }

    log(`POST -> /api/orders/checkout (Saga Initiation)...`, 'network');
    log(`Flow details: 1. check prices -> 2. gRPC stock reserve -> 3. REST pay -> 4. Publish Event or compensation rollback`, 'system');

    const requestBody = {
        items: [{ productId: prodId, quantity: quantity }],
        cardNumber: cardNumber,
        expiryDate: expiryDate,
        cvv: cvv
    };

    try {
        const res = await fetch(`${GATEWAY_URL}/api/orders/checkout`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${appState.token}`
            },
            body: JSON.stringify(requestBody)
        });

        const data = await res.json();

        if (!res.ok) {
            // Spring Boot REST Advice returns structures
            throw new Error(data.message || 'Checkout failed due to internal service issues.');
        }

        log(`SAGA SUCCESS! Order #${data.id} placed in state [${data.status}] with amount $${data.totalAmount}`, 'success');
        log(`Kafka notification published. S3 Bucket upload triggered downstream.`, 'success');
        
        // Refresh catalog and order history
        fetchProducts();
        fetchOrders();
    } catch (e) {
        log(`SAGA ROLLBACK: Transaction aborted. Details: ${e.message}`, 'error');
        log(`Rollback actions executed: gRPC Stock release locks triggered successfully.`, 'warning');
        
        // Refresh catalog and order list to show status changes
        fetchProducts();
        fetchOrders();
    }
}

// Fetch placed orders history
async function fetchOrders() {
    const tableBody = document.getElementById('orders-table-body');
    if (!tableBody) return;

    if (!appState.token) {
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-warning">Please login to view system transaction logs.</td></tr>';
        return;
    }

    tableBody.innerHTML = '';
    log("GET -> /api/orders to load history...", 'network');

    try {
        const res = await fetch(`${GATEWAY_URL}/api/orders`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${appState.token}`
            }
        });

        if (!res.ok) {
            throw new Error('Failed to retrieve transactions.');
        }

        const orders = await res.json();
        
        if (orders.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="5" class="text-center">No orders found in PostgreSQL database.</td></tr>';
            return;
        }

        // Sort descending by id
        orders.sort((a, b) => b.id - a.id);

        orders.forEach(order => {
            let statusBadge = 'badge-pending';
            if (order.status === 'PAID') statusBadge = 'badge-paid';
            if (order.status === 'FAILED') statusBadge = 'badge-failed';
            if (order.status === 'ROLLBACKED') statusBadge = 'badge-rollback';

            const invoiceButton = order.status === 'PAID' 
                ? `<button class="btn btn-sm btn-secondary" onclick="viewInvoice(${order.id})">🔍 View Invoice</button>` 
                : `<span class="text-secondary">-</span>`;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><code>#${order.id}</code></td>
                <td>${order.username}</td>
                <td>$${order.totalAmount.toFixed(2)}</td>
                <td><span class="badge ${statusBadge}">${order.status}</span></td>
                <td>${invoiceButton}</td>
            `;
            tableBody.appendChild(row);
        });
    } catch (e) {
        log(`Failed to load order history: ${e.message}`, 'error');
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-danger">Failed to communicate with Order Service.</td></tr>';
    }
}

// Download invoice directly from AWS S3 LocalStack
async function viewInvoice(orderId) {
    const displayBox = document.getElementById('invoice-display-box');
    const invoiceStatus = document.getElementById('invoice-status');

    if (!displayBox || !invoiceStatus) return;

    displayBox.innerText = `Fetching invoice_${orderId}.txt from LocalStack S3 bucket...`;
    invoiceStatus.innerText = "Downloading...";
    invoiceStatus.className = "badge badge-inactive";
    
    log(`GET -> S3 Object invoice_${orderId}.txt from ${S3_URL}...`, 'network');

    try {
        // Fetch plain-text file
        const res = await fetch(`${S3_URL}/invoice_${orderId}.txt`, {
            method: 'GET'
        });

        if (!res.ok) {
            throw new Error(`S3 Object not found or LocalStack is initializing bucket. (HTTP ${res.status})`);
        }

        const content = await res.text();
        displayBox.innerText = content;
        
        invoiceStatus.innerText = `invoice_${orderId}.txt`;
        invoiceStatus.className = "badge badge-active";
        
        log(`Invoice text generated from S3 bucket successfully.`, 'success');
    } catch (e) {
        log(`Failed to fetch S3 invoice: ${e.message}`, 'error');
        displayBox.innerText = `Error: Invoice for order #${orderId} could not be loaded.\n\nPossible Causes:\n1. The Kafka broker failed to dispatch the OrderPlacedEvent.\n2. notification-service is not running or crashed.\n3. LocalStack S3 emulator has connection issues.\n\nCheck application console logs for debugging.`;
        invoiceStatus.innerText = "Error Loading";
        invoiceStatus.className = "badge badge-failed";
    }
}

// Page load initialization
document.addEventListener('DOMContentLoaded', () => {
    // Add logout option dynamically to UI auth card if logged in
    const authCard = document.getElementById('auth-card');
    const logoutBtn = document.createElement('button');
    logoutBtn.className = "btn btn-secondary btn-sm";
    logoutBtn.style.marginTop = "10px";
    logoutBtn.innerText = "Log Out Account";
    logoutBtn.onclick = handleLogout;
    
    document.getElementById('auth-meta').appendChild(logoutBtn);

    initAuthView();
    fetchProducts();
});

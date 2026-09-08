let selectedWarehouseId = null;
let activePickOrderId = null;
let activePickLineId = null;
let authHeader = sessionStorage.getItem('wms-auth-header');
const nativeFetch = window.fetch.bind(window);
window.fetch = (input, init = {}) => {
    const headers = new Headers(init.headers || {});
    if (authHeader) headers.set('Authorization', authHeader);
    return nativeFetch(input, { ...init, headers }).then(response => {
        if (response.status === 401) showLogin();
        return response;
    });
};

document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    fetchWarehouses();
    fetchItems();
    fetchProjects();
    fetchPickOrders();
    fetchDeliveryNotes();
    fetchBins();
    document.getElementById('warehouseForm').addEventListener('submit', createWarehouse);
    document.getElementById('itemForm').addEventListener('submit', createItem);
    document.getElementById('projectForm').addEventListener('submit', createProject);
    document.getElementById('allocationForm').addEventListener('submit', createAllocation);
    document.getElementById('resetFilterBtn').addEventListener('click', resetWarehouseFilter);
    document.getElementById('themeToggle').addEventListener('click', toggleTheme);
    document.getElementById('loginForm').addEventListener('submit', login);
    document.getElementById('refreshPickOrdersBtn').addEventListener('click', fetchPickOrders);
    document.getElementById('refreshBinsBtn').addEventListener('click', fetchBins);
    document.getElementById('goodsReceiptForm').addEventListener('submit', createGoodsReceipt);
    document.getElementById('loadAuditBtn').addEventListener('click', fetchAudit);
    document.getElementById('scanForm').addEventListener('submit', submitScan);
    document.getElementById('scanModal').addEventListener('shown.bs.modal', () => document.getElementById('scanBarcode').focus());
    document.getElementById('packOrderBtn').addEventListener('click', packSelectedOrder);
    document.querySelectorAll('[data-import-type]').forEach(button => button.addEventListener('click', () => uploadCsv(button.dataset.importType)));
    if (!authHeader) showLogin();
    else loadCurrentUser();
});

function showLogin() {
    bootstrap.Modal.getOrCreateInstance(document.getElementById('loginModal')).show();
}

function login(event) {
    event.preventDefault();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    const candidate = `Basic ${btoa(`${username}:${password}`)}`;
    nativeFetch('/api/auth/me', { headers: { Authorization: candidate } }).then(response => {
        if (!response.ok) throw new Error('invalid');
        authHeader = candidate;
        sessionStorage.setItem('wms-auth-header', authHeader);
        document.getElementById('loginError').classList.add('d-none');
        bootstrap.Modal.getInstance(document.getElementById('loginModal')).hide();
        loadCurrentUser();
        fetchWarehouses(); fetchItems(); fetchProjects(); fetchPickOrders(); fetchDeliveryNotes();
    }).catch(() => document.getElementById('loginError').classList.remove('d-none'));
}

function loadCurrentUser() {
    fetch('/api/auth/me').then(ensureSuccessfulResponse).then(user => {
        document.getElementById('currentUser').textContent = `${user.username} (${user.roles.join(', ')})`;
        document.getElementById('csvImportCard').classList.toggle('d-none', !user.roles.includes('ADMIN'));
    });
}

function initializeTheme() {
    const savedTheme = localStorage.getItem('warehouse-theme');
    const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    setTheme(savedTheme || systemTheme);
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-bs-theme');
    setTheme(currentTheme === 'dark' ? 'light' : 'dark');
}

function setTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    localStorage.setItem('warehouse-theme', theme);
    const icon = document.getElementById('themeToggleIcon');
    if (icon) {
        icon.className = theme === 'dark' ? 'bi bi-sun-fill' : 'bi bi-moon-stars-fill';
    }
}

function fetchWarehouses() {
    const list = document.getElementById('warehouse-list');
    return fetch('/api/warehouses').then(ensureSuccessfulResponse).then(warehouses => {
        populateWarehouseSelect(warehouses);
        if (!warehouses.length) return showEmptyMessage(list, 5);
        list.innerHTML = warehouses.map(warehouse => `
            <tr class="warehouse-row ${warehouse.id === selectedWarehouseId ? 'table-active' : ''}" data-id="${warehouse.id}" data-name="${escapeHtml(warehouse.name)}">
                <td>${warehouse.id}</td><td>${escapeHtml(warehouse.name)}</td><td>${escapeHtml(warehouse.location)}</td>
                <td>Max: ${formatVolume(warehouse.maxSpace)} m³</td>
                <td><button class="btn btn-danger btn-sm delete-warehouse" data-id="${warehouse.id}">Löschen</button></td>
            </tr>`).join('');
        list.querySelectorAll('.warehouse-row').forEach(row => row.addEventListener('click', () => activateWarehouseFilter(row.dataset.id, row.dataset.name)));
        list.querySelectorAll('.delete-warehouse').forEach(button => button.addEventListener('click', event => {
            event.stopPropagation();
            deleteWarehouse(button.dataset.id);
        }));
    }).catch(() => showEmptyMessage(list, 5));
}

function fetchItems() {
    return fetch('/api/items').then(ensureSuccessfulResponse).then(items => {
        populateAllocationItemSelect(items);
        populateItemSelect('receiptItemSelect', items);
        populateItemSelect('auditItemSelect', items);
        renderItems(items);
    }).catch(() => showEmptyMessage(document.getElementById('item-list'), 5));
}

function populateItemSelect(id, items) {
    const select = document.getElementById(id);
    if (!select) return;
    select.innerHTML = items.length ? items.map(item => `<option value="${item.id}">${escapeHtml(item.sku)} · ${escapeHtml(item.name)}</option>`).join('') : '<option value="" disabled selected>Keine Artikel verfügbar</option>';
}

function fetchBins() {
    const list = document.getElementById('bin-list');
    return fetch('/api/storage-locations/bins').then(ensureSuccessfulResponse).then(bins => {
        const select = document.getElementById('receiptBinSelect');
        select.innerHTML = bins.length ? bins.filter(bin => bin.active).map(bin => `<option value="${bin.id}">${escapeHtml(bin.code)}</option>`).join('') : '<option value="" disabled selected>Keine aktiven Bins</option>';
        if (!bins.length) return showEmptyMessage(list, 5);
        list.innerHTML = bins.map(bin => `<tr><td>${escapeHtml(bin.code)}</td><td>${escapeHtml(bin.description)}</td><td>${formatVolume(bin.capacity)}</td><td>${bin.active ? 'Aktiv' : 'Deaktiviert'}</td><td>${escapeHtml(bin.storageLevel?.rack?.aisle?.zone?.name || '-')}</td></tr>`).join('');
    }).catch(() => showEmptyMessage(list, 5));
}

function createGoodsReceipt(event) {
    event.preventDefault();
    const receiptPayload = { receiptNumber: document.getElementById('receiptNumber').value.trim(), supplierName: document.getElementById('supplierName').value.trim(), receivedBy: document.getElementById('currentUser').textContent.split(' ')[0] || 'system' };
    sendJson('/api/goods-receipts', 'POST', receiptPayload).then(receipt => {
        const line = { inventoryItemId: Number(document.getElementById('receiptItemSelect').value), expectedQuantity: Number(document.getElementById('expectedQuantity').value), receivedQuantity: Number(document.getElementById('receivedQuantity').value), binLocationId: Number(document.getElementById('receiptBinSelect').value) };
        return sendJson(`/api/goods-receipts/${receipt.id}/lines`, 'POST', line).then(() => fetch(`/api/goods-receipts/${receipt.id}/confirm`, { method: 'POST' }).then(ensureSuccessfulResponse));
    }).then(() => { event.target.reset(); fetchItems(); fetchAudit(); }).catch(error => alert(error.message || 'Der Wareneingang konnte nicht gebucht werden.'));
}

function fetchAudit() {
    const itemId = document.getElementById('auditItemSelect').value;
    const reason = document.getElementById('auditReason').value;
    const list = document.getElementById('audit-list');
    if (!itemId) return showEmptyMessage(list, 5);
    const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    fetch(`/api/items/${itemId}/transactions${query}`).then(ensureSuccessfulResponse).then(transactions => {
        if (!transactions.length) return showEmptyMessage(list, 5);
        list.innerHTML = transactions.map(transaction => `<tr><td>${escapeHtml(transaction.timestamp)}</td><td>${escapeHtml(transaction.reason)}</td><td>${transaction.quantityDelta > 0 ? '+' : ''}${transaction.quantityDelta}</td><td>${transaction.previousQuantity} → ${transaction.newQuantity}</td><td>${escapeHtml(transaction.username)}</td></tr>`).join('');
    }).catch(() => showEmptyMessage(list, 5));
}

function fetchItemsByWarehouse(warehouseId) {
    return fetch(`/api/warehouses/${warehouseId}/items`).then(ensureSuccessfulResponse).then(renderItems)
        .catch(() => showEmptyMessage(document.getElementById('item-list'), 5));
}

function renderItems(items) {
    const list = document.getElementById('item-list');
    if (!items.length) return showEmptyMessage(list, 5);
    list.innerHTML = items.map(item => {
        const available = item.availableQuantity ?? item.quantityInStock;
        const lowStock = item.quantityInStock > 0 && available / item.quantityInStock < 0.2;
        return `<tr>
            <td>${item.id}</td><td><strong>${escapeHtml(item.sku)}</strong><br>${escapeHtml(item.name)}${item.barcode ? `<br><small class="text-body-secondary">${escapeHtml(item.barcode)}</small>` : ''}</td>
            <td class="${lowStock ? 'text-danger fw-bold' : ''}">${available} / ${item.quantityInStock}</td>
            <td>${escapeHtml(item.warehouse?.name || 'Nicht zugewiesen')}</td>
            <td><button class="btn btn-danger btn-sm delete-item" data-id="${item.id}">Löschen</button></td>
        </tr>`;
    }).join('');
    list.querySelectorAll('.delete-item').forEach(button => button.addEventListener('click', () => deleteItem(button.dataset.id)));
}

function fetchProjects() {
    const list = document.getElementById('project-list');
    return fetch('/api/projects').then(ensureSuccessfulResponse).then(projects => {
        populateAllocationProjectSelect(projects);
        if (!projects.length) return showEmptyMessage(list, 6);
        list.innerHTML = projects.map(project => `
            <tr>
                <td>${project.id}</td><td><strong>${escapeHtml(project.orderNumber || '')}</strong><br>${escapeHtml(project.name)}</td><td>${escapeHtml(project.description)}</td>
                <td>${renderResources(project.allocations)}</td>
                <td>${statusBadge(project.status)}</td>
                <td>${renderProjectActions(project)}</td>
            </tr>`).join('');
        list.querySelectorAll('[data-project-status]').forEach(button => button.addEventListener('click', () =>
            changeProjectStatus(button.dataset.projectId, button.dataset.projectStatus)));
        list.querySelectorAll('[data-project-pick]').forEach(button => button.addEventListener('click', () => createPickOrder(button.dataset.projectPick)));
    }).catch(() => showEmptyMessage(list, 6));
}

function renderResources(allocations = []) {
    if (!allocations.length) return '<span class="text-muted">Keine Ressourcen</span>';
    return allocations.map(allocation => `<span class="badge text-bg-light border text-dark me-1">${escapeHtml(allocation.inventoryItem?.name || 'Item')} × ${allocation.allocatedQuantity}</span>`).join('');
}

function statusBadge(status) {
    const badgeClasses = {
        PENDING: 'text-bg-warning',
        APPROVED: 'text-bg-primary',
        IN_PROGRESS: 'text-bg-primary',
        COMPLETED: 'text-bg-success',
        REJECTED: 'text-bg-danger'
    };
    return `<span class="badge rounded-pill ${badgeClasses[status] || 'text-bg-secondary'} px-3 py-2">${escapeHtml(status)}</span>`;
}

function renderProjectActions(project) {
    if (project.status === 'PENDING') {
        return statusButton(project.id, 'APPROVED', 'Genehmigen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger');
    }
    if (project.status === 'APPROVED') {
        return statusButton(project.id, 'IN_PROGRESS', 'Starten', 'btn-primary') + statusButton(project.id, 'COMPLETED', 'Abschließen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger') + pickButton(project.id);
    }
    if (project.status === 'IN_PROGRESS') {
        return statusButton(project.id, 'COMPLETED', 'Abschließen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger') + pickButton(project.id);
    }
    return '<span class="text-muted">Keine Aktionen</span>';
}

function pickButton(projectId) {
    return `<button class="btn btn-outline-primary btn-sm rounded-pill me-1 mb-1" data-project-pick="${projectId}"><i class="bi bi-list-check me-1"></i>Pickauftrag</button>`;
}

function statusButton(projectId, status, label, cssClass) {
    return `<button class="btn ${cssClass} btn-sm rounded-pill me-1 mb-1" data-project-id="${projectId}" data-project-status="${status}">${label}</button>`;
}

function changeProjectStatus(projectId, newStatus) {
    fetch(`/api/projects/${projectId}/status?newStatus=${newStatus}`, { method: 'PATCH' })
        .then(ensureSuccessfulResponse)
        .then(() => Promise.all([fetchProjects(), refreshVisibleItems()]))
        .catch(error => alert(error.message || 'Der Auftragsstatus konnte nicht geändert werden.'));
}

function activateWarehouseFilter(warehouseId, warehouseName) {
    selectedWarehouseId = Number(warehouseId);
    document.querySelectorAll('.warehouse-row').forEach(row => row.classList.toggle('table-active', Number(row.dataset.id) === selectedWarehouseId));
    document.getElementById('inventoryTitle').textContent = `Inventar (Lager: ${warehouseName})`;
    document.getElementById('resetFilterBtn').classList.remove('d-none');
    fetchItemsByWarehouse(selectedWarehouseId);
}

function resetWarehouseFilter() {
    selectedWarehouseId = null;
    document.getElementById('inventoryTitle').textContent = 'Inventar';
    document.getElementById('resetFilterBtn').classList.add('d-none');
    document.querySelectorAll('.warehouse-row').forEach(row => row.classList.remove('table-active'));
    fetchItems();
}

function refreshVisibleItems() {
    return selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : fetchItems();
}

function populateWarehouseSelect(warehouses) {
    const select = document.getElementById('warehouseSelect');
    select.innerHTML = warehouses.length ? warehouses.map(warehouse => `<option value="${warehouse.id}">${escapeHtml(warehouse.name)}</option>`).join('') : '<option value="" disabled selected>Keine Lager verfügbar</option>';
}

function populateAllocationItemSelect(items) {
    const select = document.getElementById('allocationItemSelect');
    select.innerHTML = items.length ? items.map(item => `<option value="${item.id}">${escapeHtml(item.name)} (${item.availableQuantity ?? item.quantityInStock} verfügbar)</option>`).join('') : '<option value="" disabled selected>Keine Items verfügbar</option>';
}

function populateAllocationProjectSelect(projects) {
    const select = document.getElementById('allocationProjectSelect');
    const pendingProjects = projects.filter(project => project.status === 'PENDING');
    select.innerHTML = pendingProjects.length ? pendingProjects.map(project => `<option value="${project.id}">${escapeHtml(project.name)}</option>`).join('') : '<option value="" disabled selected>Keine ausstehenden Aufträge</option>';
}

function fetchPickOrders() {
    const list = document.getElementById('pick-order-list');
    return fetch('/api/pick-orders').then(ensureSuccessfulResponse).then(orders => {
        populatePackOrderSelect(orders);
        if (!orders.length) return showEmptyMessage(list, 5);
        list.innerHTML = orders.map(order => `
            <tr>
                <td>${order.id}</td>
                <td><strong>${escapeHtml(order.project?.orderNumber || '')}</strong><br>${escapeHtml(order.project?.name || 'Auftrag')}</td>
                <td>${statusBadge(order.status)}</td>
                <td>${renderPickLines(order)}</td>
                <td class="text-end">${renderPickActions(order)}</td>
            </tr>`).join('');
        list.querySelectorAll('[data-pick-start]').forEach(button => button.addEventListener('click', () => startPickOrder(button.dataset.pickStart)));
        list.querySelectorAll('[data-pick-complete]').forEach(button => button.addEventListener('click', () => completePickOrder(button.dataset.pickComplete)));
        list.querySelectorAll('[data-pick-line]').forEach(button => button.addEventListener('click', () => openScanDialog(button.dataset.pickOrder, button.dataset.pickLine, button.dataset.pickItem, button.dataset.pickLocation)));
    }).catch(() => showEmptyMessage(list, 5));
}

function populatePackOrderSelect(orders) {
    const select = document.getElementById('packPickOrderSelect');
    const completed = orders.filter(order => order.status === 'COMPLETED');
    select.innerHTML = completed.length ? completed.map(order => `<option value="${order.id}">${escapeHtml(order.project?.orderNumber || `Pickauftrag ${order.id}`)}</option>`).join('') : '<option value="" disabled selected>Keine gepickten Aufträge</option>';
}

function fetchDeliveryNotes() {
    const list = document.getElementById('delivery-note-list');
    return fetch('/api/delivery-notes').then(ensureSuccessfulResponse).then(notes => {
        if (!notes.length) return showEmptyMessage(list, 4);
        list.innerHTML = notes.map(note => `<tr><td>${escapeHtml(note.documentNumber)}</td><td>${escapeHtml(note.recipient || '-')}</td><td>${statusBadge(note.packStatus)}</td><td class="text-end"><a class="btn btn-sm btn-outline-secondary" href="/api/delivery-notes/${note.id}/pdf" title="Lieferschein herunterladen"><i class="bi bi-file-earmark-pdf"></i><span class="visually-hidden">PDF</span></a></td></tr>`).join('');
    }).catch(() => showEmptyMessage(list, 4));
}

function packSelectedOrder() {
    const pickOrderId = document.getElementById('packPickOrderSelect').value;
    if (!pickOrderId) return alert('Es ist kein gepickter Auftrag ausgewählt.');
    fetch(`/api/delivery-notes/from-pick-order/${pickOrderId}/pack`, { method: 'POST' })
        .then(ensureSuccessfulResponse)
        .then(() => Promise.all([fetchPickOrders(), fetchDeliveryNotes(), fetchItems()]))
        .catch(error => alert(error.message || 'Der Auftrag konnte nicht gepackt werden.'));
}

function uploadCsv(type) {
    const inputId = type === 'items' ? 'itemsCsv' : type === 'projects' ? 'projectsCsv' : 'projectLinesCsv';
    const file = document.getElementById(inputId).files[0];
    if (!file) return alert('Bitte zuerst eine CSV-Datei auswählen.');
    const formData = new FormData();
    formData.append('file', file);
    fetch(`/api/import/${type}`, { method: 'POST', body: formData }).then(response => response.json().then(body => ({ ok: response.ok, body }))).then(result => {
        if (!result.ok) throw new Error((result.body.errors || ['Der Import ist fehlgeschlagen.']).join('\n'));
        alert(`${result.body.importedRows} Zeilen erfolgreich importiert.`);
        fetchWarehouses();
        fetchItems();
        fetchProjects();
    }).catch(error => alert(error.message));
}

function renderPickLines(order) {
    return (order.lines || []).map(line => {
        const scanButton = order.status === 'IN_PROGRESS' && line.status !== 'PICKED'
            ? `<button class="btn btn-sm btn-outline-primary rounded-pill ms-2" data-pick-order="${order.id}" data-pick-line="${line.id}" data-pick-item="${escapeHtml(line.inventoryItem?.name || 'Artikel')}" data-pick-location="${escapeHtml(line.binLocation?.code || 'Kein Bin')}"><i class="bi bi-upc-scan"></i><span class="visually-hidden">Position scannen</span></button>` : '';
        return `<div class="mb-1"><span>${escapeHtml(line.binLocation?.code || 'Kein Bin')} · ${escapeHtml(line.inventoryItem?.name || 'Artikel')} · ${line.pickedQuantity}/${line.requiredQuantity}</span>${statusBadge(line.status)}${scanButton}</div>`;
    }).join('');
}

function renderPickActions(order) {
    if (order.status === 'CREATED') return `<button class="btn btn-primary btn-sm rounded-pill" data-pick-start="${order.id}"><i class="bi bi-play-fill me-1"></i>Starten</button>`;
    if (order.status === 'IN_PROGRESS') return `<button class="btn btn-success btn-sm rounded-pill" data-pick-complete="${order.id}"><i class="bi bi-check2 me-1"></i>Abschließen</button>`;
    return '<span class="text-muted">Keine Aktionen</span>';
}

function createPickOrder(projectId) {
    fetch(`/api/pick-orders/from-project/${projectId}`, { method: 'POST' })
        .then(ensureSuccessfulResponse)
        .then(() => fetchPickOrders())
        .catch(error => alert(error.message || 'Der Pickauftrag konnte nicht erstellt werden.'));
}

function startPickOrder(pickOrderId) {
    fetch(`/api/pick-orders/${pickOrderId}/start`, { method: 'POST' })
        .then(ensureSuccessfulResponse)
        .then(() => fetchPickOrders())
        .catch(error => alert(error.message || 'Der Pickauftrag konnte nicht gestartet werden.'));
}

function completePickOrder(pickOrderId) {
    fetch(`/api/pick-orders/${pickOrderId}/complete`, { method: 'POST' })
        .then(ensureSuccessfulResponse)
        .then(() => fetchPickOrders())
        .catch(error => alert(error.message || 'Der Pickauftrag ist noch nicht vollständig gepickt.'));
}

function openScanDialog(pickOrderId, pickLineId, itemName, locationCode) {
    activePickOrderId = pickOrderId;
    activePickLineId = pickLineId;
    document.getElementById('scanExpectedItem').textContent = itemName;
    document.getElementById('scanExpectedLocation').textContent = `Lagerplatz: ${locationCode}`;
    document.getElementById('scanBarcode').value = '';
    document.getElementById('scanQuantity').value = '1';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('scanModal')).show();
}

function submitScan(event) {
    event.preventDefault();
    const payload = { barcode: document.getElementById('scanBarcode').value.trim(), quantity: Number(document.getElementById('scanQuantity').value) };
    sendJson(`/api/pick-orders/${activePickOrderId}/lines/${activePickLineId}/scan`, 'POST', payload)
        .then(() => {
            bootstrap.Modal.getInstance(document.getElementById('scanModal')).hide();
            return fetchPickOrders();
        })
        .catch(error => {
            document.getElementById('scanBarcode').select();
            alert(error.message || 'Der Scan wurde abgelehnt.');
        });
}

function createWarehouse(event) {
    event.preventDefault();
    const payload = { name: document.getElementById('warehouseName').value.trim(), location: document.getElementById('warehouseLocation').value.trim(), maxSpace: Number(document.getElementById('maxSpace').value) };
    sendJson('/api/warehouses', 'POST', payload).then(() => closeAndReset('warehouseModal', event.target)).then(fetchWarehouses)
        .catch(error => alert(error.message || 'Das Lager konnte nicht gespeichert werden.'));
}

function createItem(event) {
    event.preventDefault();
    const optionalNumber = id => document.getElementById(id).value === '' ? null : Number(document.getElementById(id).value);
    const payload = { sku: document.getElementById('itemSku').value.trim(), barcode: document.getElementById('itemBarcode').value.trim() || null, name: document.getElementById('itemName').value.trim(), quantityInStock: Number(document.getElementById('itemQuantityInStock').value), spacePerUnit: Number(document.getElementById('spacePerUnit').value), length: optionalNumber('itemLength'), width: optionalNumber('itemWidth'), height: optionalNumber('itemHeight'), warehouseId: Number(document.getElementById('warehouseSelect').value) };
    sendJson('/api/items', 'POST', payload).then(() => closeAndReset('itemModal', event.target)).then(() => {
        return fetchItems().then(() => selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : null);
    }).catch(error => alert(error.message || 'Das Item konnte nicht gespeichert werden.'));
}

function createProject(event) {
    event.preventDefault();
    const payload = { orderNumber: document.getElementById('projectOrderNumber').value.trim() || null, name: document.getElementById('projectName').value.trim(), customerName: document.getElementById('projectCustomerName').value.trim() || null, customerEmail: document.getElementById('projectCustomerEmail').value.trim() || null, deliveryAddress: document.getElementById('projectDeliveryAddress').value.trim() || null, description: document.getElementById('projectDescription').value.trim() };
    sendJson('/api/projects', 'POST', payload).then(() => closeAndReset('projectModal', event.target)).then(fetchProjects)
        .catch(error => alert(error.message || 'Der Auftrag konnte nicht gespeichert werden.'));
}

function createAllocation(event) {
    event.preventDefault();
    const projectId = document.getElementById('allocationProjectSelect').value;
    const payload = { inventoryItemId: Number(document.getElementById('allocationItemSelect').value), allocatedQuantity: Number(document.getElementById('allocationQuantity').value) };
    sendJson(`/api/projects/${projectId}/allocations`, 'POST', payload).then(() => closeAndReset('allocationModal', event.target)).then(fetchProjects)
        .catch(error => alert(error.message || 'Die Ressource konnte nicht zugewiesen werden.'));
}

function deleteWarehouse(id) {
    fetch(`/api/warehouses/${id}`, { method: 'DELETE' }).then(ensureSuccessfulResponse).then(() => {
        if (Number(id) === selectedWarehouseId) resetWarehouseFilter();
        return fetchWarehouses();
    }).catch(() => alert('Das Lager konnte nicht gelöscht werden. Lösche zuerst die zugeordneten Items.'));
}

function deleteItem(id) {
    fetch(`/api/items/${id}`, { method: 'DELETE' }).then(ensureSuccessfulResponse).then(() => {
        return fetchItems().then(() => selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : null);
    }).catch(() => alert('Das Item konnte nicht gelöscht werden.'));
}

function closeAndReset(modalId, form) {
    bootstrap.Modal.getInstance(document.getElementById(modalId)).hide();
    form.reset();
}

function sendJson(url, method, payload) {
    return fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(ensureSuccessfulResponse);
}

function ensureSuccessfulResponse(response) {
    if (!response.ok) return response.text().then(message => Promise.reject(new Error(message || `HTTP ${response.status}`)));
    return response.status === 204 ? null : response.json();
}

function showEmptyMessage(tableBody, columnCount) {
    tableBody.innerHTML = `<tr><td colspan="${columnCount}" class="text-center text-muted">Keine Daten verfügbar</td></tr>`;
}

function formatVolume(value) {
    return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 }).format(value ?? 0);
}

function escapeHtml(value) {
    const element = document.createElement('div');
    element.textContent = value ?? '';
    return element.innerHTML;
}

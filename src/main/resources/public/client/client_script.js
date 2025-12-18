const API = '/api';

async function refresh() {
    try {
        const res = await fetch(`${API}/status`);
        const data = await res.json();
        updateUI(data);
    } catch (e) {
        console.error(e);
    }
}

async function configureClient() {
    const region = document.getElementById('config-region').value;
    const id = document.getElementById('config-id').value;
    if (!region || !id) {
        alert("Please enter both Region and ID.");
        return;
    }

    try {
        const res = await fetch(`${API}/configure?id=${encodeURIComponent(id)}&region=${encodeURIComponent(region)}`);
        const json = await res.json();
        if (json.status === 'configured') {
            document.getElementById('config-modal').style.display = 'none';
            // Trigger auto connect after config
            action('connect');
        }
    } catch (e) {
        console.error("Config failed", e);
    }
}

function updateUI(data) {
    const badge = document.getElementById('status-badge');
    const modal = document.getElementById('config-modal');

    // Auto-prompt for configuration if newly connected but not yet identified
    // Check if configured (if region is 'Unknown' or nodeID is the default 'addis_001')
    const isNew = data.region === 'Unknown' || data.nodeId === 'addis_001';

    if (data.connected && isNew && modal.style.display !== 'flex') {
        modal.style.display = 'flex'; // Show modal for new clients upon connection
    } else if (!isNew) {
        modal.style.display = 'none';
    }

    // Displays
    document.getElementById('display-id').textContent = data.nodeId;
    document.getElementById('display-region').textContent = data.region;
    document.getElementById('display-voltage').textContent = data.voltage.toFixed(1) + ' V';
    document.getElementById('display-power-state').textContent = data.powerState;

    // Status Badge
    if (data.connected) {
        badge.textContent = 'ONLINE';
        badge.className = 'status-badge connected';
        document.getElementById('btn-reconnect').style.display = 'none';
    } else {
        badge.textContent = 'OFFLINE';
        badge.className = 'status-badge disconnected';
        document.getElementById('btn-reconnect').style.display = 'inline-block';
    }

    // POLL FOR POPUPS
    if (data.logs && data.logs.length > 0) {
        processLogs(data.logs);
    }
}

let lastLogCount = 0;

function processLogs(logs) {
    // If fewer logs than before, reset (maybe disconnect/reconnect cleared logs)
    if (logs.length < lastLogCount) {
        lastLogCount = 0;
    }

    // Process only NEW logs
    for (let i = lastLogCount; i < logs.length; i++) {
        const log = logs[i];
        if (log.includes("HQ is inquiring")) {
            // Delay slightly to ensure UI updates first, or just alert
            setTimeout(() => {
                alert("🔔 MESSAGE FROM SERVER:\n\nServer is requesting verification.\nIs the problem solved?");
            }, 100);
        }
    }
    lastLogCount = logs.length;
}


async function action(act) {
    console.log("Sending action:", act);
    await fetch(`${API}/action?action=${act}`, { method: 'POST' });
    refresh();
}

async function setManualVoltage() {
    const v = document.getElementById('input-voltage').value;
    if (!v) return;
    await fetch(`${API}/set_voltage?v=${v}`);
    refresh();
}

setInterval(refresh, 1000);
refresh();

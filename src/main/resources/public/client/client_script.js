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

function updateUI(data) {
    const badge = document.getElementById('status-badge');
    if (data.connected) {
        badge.textContent = 'Connected (' + data.nodeId + ')';
        badge.className = 'status-badge connected';
        document.getElementById('btn-connect').disabled = true;
        document.getElementById('btn-disconnect').disabled = false;
    } else {
        badge.textContent = 'Disconnected';
        badge.className = 'status-badge disconnected';
        document.getElementById('btn-connect').disabled = false;
        document.getElementById('btn-disconnect').disabled = true;
    }

    const logContainer = document.getElementById('log-container');
    logContainer.innerHTML = '';
    data.logs.slice().reverse().forEach(log => {
        const div = document.createElement('div');
        div.className = 'log-entry';
        div.textContent = log;
        logContainer.appendChild(div);
    });
}

async function action(act) {
    await fetch(`${API}/action?action=${act}`, { method: 'POST' });
    refresh();
}

setInterval(refresh, 1000);
refresh();

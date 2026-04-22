// ========== Configuration ==========
const API_BASE = '';

// ========== DOM Elements ==========
const aiLayer = document.getElementById('aiLayer');
const chatMessages = document.getElementById('chatMessages');
const chatEmpty = document.getElementById('chatEmpty');
const messageInput = document.getElementById('messageInput');
const historyList = document.getElementById('historyList');
const pdfModal = document.getElementById('pdfModal');
const dropZone = document.getElementById('dropZone');
const medicationNotification = document.getElementById('medicationNotification');

// ========== State ==========
let currentSessionId = localStorage.getItem('medical_session_id') || null;
let sessions = [];
let pendingRecordId = null;
let pollingInterval = null;

// ========== Initialize ==========
function init() {
    // 等待 marked 库加载完成
    if (typeof marked !== 'undefined') {
        marked.setOptions({ breaks: true, gfm: true });
    }

    const token = localStorage.getItem('token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }
    loadSessions();
    startPolling();
}

// ========== AI Layer ==========
function openAI() {
    aiLayer.classList.add('active');
    document.body.style.overflow = 'hidden';
    messageInput.focus();
}

function closeAI() {
    aiLayer.classList.remove('active');
    document.body.style.overflow = '';
}

// ========== Chat Functions ==========
function handleKeyPress(e) {
    if (e.key === 'Enter') sendMessage();
}

async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message) return;

    chatEmpty.style.display = 'none';
    addChatMessage('user', message, Date.now());
    messageInput.value = '';

    const aiMsgId = 'ai-' + Date.now();
    chatMessages.insertAdjacentHTML('beforeend',
        `<div id="${aiMsgId}" class="chat-message assistant">
            <div class="chat-bubble"><span class="streaming-cursor">▊</span></div>
        </div>`
    );
    scrollToBottom();

    let fullResponse = '';
    const contentDiv = document.getElementById(aiMsgId).querySelector('.chat-bubble');

    try {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/stream/medical/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                sessionId: currentSessionId || null,
                message: message
            })
        });

        if (!response.ok) throw new Error('HTTP ' + response.status);

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let dataBuffer = [];
        let currentEvent = 'message';

        while (true) {
            const { done, value } = await reader.read();
            if (value) buffer += decoder.decode(value, { stream: true });

            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (line.startsWith('event:')) {
                    if (dataBuffer.length > 0) {
                        processData(currentEvent, dataBuffer.join('\n'));
                        dataBuffer = [];
                    }
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                    let data = line.substring(5);
                    if (data.startsWith(' ')) data = data.substring(1);
                    dataBuffer.push(data);
                } else if (line === '') {
                    if (dataBuffer.length > 0) {
                        processData(currentEvent, dataBuffer.join('\n'));
                        dataBuffer = [];
                    }
                    currentEvent = 'message';
                }
            }

            if (done) {
                if (dataBuffer.length > 0) processData(currentEvent, dataBuffer.join('\n'));
                contentDiv.innerHTML = renderMarkdown(fullResponse) + `<span class="message-time">${formatTime(Date.now())}</span>`;
                break;
            }
        }

        function processData(event, data) {
            if (event === 'message') {
                fullResponse += data;
                contentDiv.textContent = fullResponse;
            } else if (event === 'done') {
                try {
                    const result = JSON.parse(data);
                    if (result.sessionId) {
                        currentSessionId = result.sessionId;
                        localStorage.setItem('medical_session_id', currentSessionId);
                    }
                } catch (e) {}
                loadSessions();
            } else if (event === 'error') {
                contentDiv.innerHTML = `<span style="color:var(--error)">错误: ${escapeHtml(data)}</span>`;
            }
        }
    } catch (e) {
        contentDiv.innerHTML = `<span style="color:var(--error)">请求失败: ${escapeHtml(e.message)}</span>`;
    }

    scrollToBottom();
}

function addChatMessage(role, text, timestamp) {
    const div = document.createElement('div');
    div.className = `chat-message ${role}`;
    const timeStr = timestamp ? `<span class="message-time">${formatTime(timestamp)}</span>` : '';
    div.innerHTML = `<div class="chat-bubble">${role === 'user' ? escapeHtml(text) : renderMarkdown(text)}${timeStr}</div>`;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function renderMarkdown(text) {
    return marked.parse(escapeHtml(text));
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}

// ========== History Functions ==========
function getCurrentUserId() {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.sub || payload.username || payload.id || 'default_user';
    } catch (e) {
        return 'default_user';
    }
}

async function loadSessions() {
    try {
        const token = localStorage.getItem('token');
        // 不再传 userId 参数，后端从 JWT token 自动获取
        const resp = await fetch('/api/memory/sessions', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            sessions = await resp.json();
            renderHistoryList();
        }
    } catch (e) {
        console.error('加载历史对话失败:', e);
    }
}

function formatTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return '刚刚';
    if (diffMins < 60) return `${diffMins}分钟前`;
    if (diffHours < 24) return `${diffHours}小时前`;
    if (diffDays < 7) return `${diffDays}天前`;

    const month = date.getMonth() + 1;
    const day = date.getDate();
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');

    if (date.getFullYear() === now.getFullYear()) {
        return `${month}/${day} ${hours}:${minutes}`;
    }
    return `${date.getFullYear()}/${month}/${day} ${hours}:${minutes}`;
}

function renderHistoryList() {
    if (!sessions || sessions.length === 0) {
        historyList.innerHTML = '<div class="history-empty">暂无历史对话</div>';
        return;
    }

    historyList.innerHTML = sessions.map(s => `
        <div class="history-item ${s.sessionId === currentSessionId ? 'active' : ''}" onclick="selectSession('${escapeHtml(s.sessionId).replace(/'/g, "\\'")}')">
            <div class="history-item-content">
                <span class="history-item-title">${escapeHtml(s.title || '未命名对话')}</span>
                <span class="history-item-time">${formatTime(s.lastActiveAt)}</span>
            </div>
            <span class="history-item-delete" onclick="deleteSession('${escapeHtml(s.sessionId).replace(/'/g, "\\'")}', event)">删除</span>
        </div>
    `).join('');
}

async function selectSession(sessionId) {
    currentSessionId = sessionId;
    localStorage.setItem('medical_session_id', sessionId);
    chatMessages.innerHTML = '';
    chatEmpty.style.display = 'none';

    try {
        const token = localStorage.getItem('token');
        const resp = await fetch(`/api/memory/session/${encodeURIComponent(sessionId)}?limit=50&offset=0`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            const detail = await resp.json();
            if (detail.messages) {
                detail.messages.forEach(msg => {
                    addChatMessage(msg.role === 'user' ? 'user' : 'assistant', msg.content, msg.timestamp);
                });
            }
        }
    } catch (e) {
        console.error('加载会话详情失败:', e);
    }
    renderHistoryList();
}

async function deleteSession(sessionId, event) {
    event.stopPropagation();
    if (!confirm('确定要删除该会话吗？')) return;

    try {
        const token = localStorage.getItem('token');
        const resp = await fetch(`/api/memory/session/${encodeURIComponent(sessionId)}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            if (currentSessionId === sessionId) {
                currentSessionId = null;
                localStorage.removeItem('medical_session_id');
                chatMessages.innerHTML = '';
                chatEmpty.style.display = 'flex';
            }
            loadSessions();
        }
    } catch (e) {
        console.error('删除会话失败:', e);
    }
}

function newChat() {
    currentSessionId = null;
    localStorage.removeItem('medical_session_id');
    chatMessages.innerHTML = '';
    chatEmpty.style.display = 'flex';
    renderHistoryList();
}

// ========== Medication Notification ==========
function startPolling() {
    checkPendingReminders();
    pollingInterval = setInterval(checkPendingReminders, 30000);
}

async function checkPendingReminders() {
    const token = localStorage.getItem('token');
    if (!token) return;

    try {
        const resp = await fetch('/api/medication/pending', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            const data = await resp.json();
            if (data.length > 0) {
                showMedicationNotification(data[0]);
            }
        }
    } catch (e) {
        console.error('轮询失败:', e);
    }
}

function showMedicationNotification(record) {
    pendingRecordId = record.id;
    const time = record.scheduledTime ? record.scheduledTime.substring(11, 16) : '';
    document.getElementById('notificationText').textContent =
        `该服用 ${record.medicationName} ${record.dosage || ''} (${time})`;
    medicationNotification.classList.add('active');
}

function closeNotification() {
    medicationNotification.classList.remove('active');
    pendingRecordId = null;
}

async function confirmMedication() {
    if (!pendingRecordId) return;
    const token = localStorage.getItem('token');

    try {
        const resp = await fetch(`/api/medication/records/${pendingRecordId}/confirm`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            closeNotification();
            checkPendingReminders();
        }
    } catch (e) {
        alert('操作失败');
    }
}

async function skipMedication() {
    if (!pendingRecordId) return;
    const token = localStorage.getItem('token');

    try {
        const resp = await fetch(`/api/medication/records/${pendingRecordId}/skip`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (resp.ok) {
            closeNotification();
            checkPendingReminders();
        }
    } catch (e) {
        alert('操作失败');
    }
}

// ========== PDF Upload ==========
function openPdfUpload() {
    pdfModal.classList.add('active');
    resetUploadState();
}

function closePdfUpload() {
    pdfModal.classList.remove('active');
}

function resetUploadState() {
    document.getElementById('pdfInput').value = '';
    document.getElementById('uploadProgress').classList.remove('active');
    document.getElementById('uploadResult').className = 'upload-result';
    document.getElementById('uploadResult').textContent = '';
}

function handleFileSelect(file) {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.pdf')) {
        showUploadResult(false, '仅支持 PDF 格式文件');
        return;
    }
    uploadPdf(file);
}

async function uploadPdf(file) {
    const progressDiv = document.getElementById('uploadProgress');
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');

    progressDiv.classList.add('active');

    let progress = 0;
    const interval = setInterval(() => {
        progress = Math.min(progress + Math.random() * 15, 90);
        progressBar.style.width = progress + '%';
        progressText.textContent = Math.round(progress) + '%';
    }, 200);

    try {
        const formData = new FormData();
        formData.append('file', file);

        const resp = await fetch('/api/pdf/upload', {
            method: 'POST',
            body: formData
        });

        clearInterval(interval);
        progressBar.style.width = '100%';
        progressText.textContent = '100%';

        const result = await resp.json();
        if (result.success) {
            showUploadResult(true, '上传成功！文档已切分为 ' + result.totalChunks + ' 个片段');
        } else {
            showUploadResult(false, result.message || '上传失败');
        }
    } catch (e) {
        clearInterval(interval);
        showUploadResult(false, '上传失败: ' + e.message);
    }
}

function showUploadResult(success, message) {
    const resultDiv = document.getElementById('uploadResult');
    resultDiv.className = 'upload-result ' + (success ? 'success' : 'error');
    resultDiv.textContent = message;
}

// Drag & Drop
dropZone.addEventListener('dragover', e => {
    e.preventDefault();
    dropZone.style.borderColor = 'var(--accent-primary)';
    dropZone.style.background = 'rgba(0, 212, 255, 0.03)';
});

dropZone.addEventListener('dragleave', () => {
    dropZone.style.borderColor = '';
    dropZone.style.background = '';
});

dropZone.addEventListener('drop', e => {
    e.preventDefault();
    dropZone.style.borderColor = '';
    dropZone.style.background = '';
    const file = e.dataTransfer.files[0];
    if (file) handleFileSelect(file);
});

// ========== Logout ==========
function handleLogout() {
    localStorage.removeItem('token');
    localStorage.removeItem('medical_session_id');
    window.location.href = '/login.html';
}

// ========== Initialize ==========
init();

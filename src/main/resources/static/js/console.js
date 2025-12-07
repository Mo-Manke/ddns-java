document.addEventListener('DOMContentLoaded', function () {
    // 存储IP服务列表
    let ipServicesList = [];

    // ==================== 删除账号按钮 ====================
    document.querySelectorAll('.delete-account-btn').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation(); // 阻止冒泡，避免触发菜单点击
            
            const provider = this.getAttribute('data-provider');
            const id = this.getAttribute('data-id');
            
            if (confirm(`确定要删除 ${provider} 账号吗？\n\nSecretId: ${id.substring(0, 10)}...`)) {
                // 创建表单提交
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = '/deleteAccount';
                
                const providerInput = document.createElement('input');
                providerInput.type = 'hidden';
                providerInput.name = 'provider';
                providerInput.value = provider;
                form.appendChild(providerInput);
                
                const idInput = document.createElement('input');
                idInput.type = 'hidden';
                idInput.name = 'id';
                idInput.value = id;
                form.appendChild(idInput);
                
                document.body.appendChild(form);
                form.submit();
            }
        });
    });

    // ==================== 页面切换 ====================
    const menuItems = document.querySelectorAll('.menu li');

    menuItems.forEach(item => {
        item.addEventListener('click', function(e) {
            e.preventDefault();
            const pageId = this.getAttribute('data-page');
            
            // 更新菜单状态
            menuItems.forEach(li => li.classList.remove('active'));
            this.classList.add('active');
            
            // 更新页面显示
            document.querySelectorAll('.page').forEach(page => {
                page.classList.remove('active');
            });
            document.getElementById(pageId)?.classList.add('active');
            
            // 如果是用户账号项，加载该账号的域名列表和IP
            if (pageId === 'dns-manage') {
                const secretId = this.querySelector('.user-id')?.value;
                const secretKey = this.querySelector('.user-key')?.value;
                const provider = this.querySelector('.user-provider')?.value;
                
                if (secretId && secretKey) {
                    document.getElementById('currentSecretId').value = secretId;
                    document.getElementById('currentSecretKey').value = secretKey;
                    document.getElementById('currentProvider').value = provider || '腾讯云';
                    loadDomainList(secretId, secretKey);
                    loadAllIps();
                    loadDdnsTasks(secretId);
                }
            }
        });
    });

    // ==================== 获取所有IP服务结果 ====================
    function loadAllIps() {
        const ipListDiv = document.getElementById('ipList');
        ipListDiv.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 正在获取IP...</div>';
        
        fetch('/api/dns/allIps')
            .then(response => response.json())
            .then(data => {
                if (data.code === 200 && Array.isArray(data.data)) {
                    ipServicesList = data.data;
                    renderIpList(data.data);
                    updateIpServiceSelects();
                    const successCount = data.data.filter(i => i.status === 'success').length;
                    addLog(`获取公网IP完成，${successCount}/${data.data.length} 个服务可用`, 'success');
                } else {
                    ipListDiv.innerHTML = '<div class="error"><i class="fas fa-exclamation-circle"></i> 获取失败</div>';
                    addLog('获取公网IP失败', 'error');
                }
            })
            .catch(error => {
                console.error('获取IP失败:', error);
                ipListDiv.innerHTML = '<div class="error"><i class="fas fa-exclamation-circle"></i> 请求失败</div>';
                addLog('获取公网IP请求失败: ' + error.message, 'error');
            });
    }

    // 更新IP服务下拉框
    function updateIpServiceSelects() {
        const selects = ['ddnsIpService', 'editDdnsIpService'];
        selects.forEach(selectId => {
            const select = document.getElementById(selectId);
            if (select) {
                select.innerHTML = '';
                ipServicesList.forEach(item => {
                    if (item.status === 'success') {
                        const option = document.createElement('option');
                        option.value = item.url;
                        option.textContent = `${item.name} (${item.ip})`;
                        option.dataset.name = item.name;
                        select.appendChild(option);
                    }
                });
            }
        });
    }

    // 渲染IP列表
    function renderIpList(ipResults) {
        const ipListDiv = document.getElementById('ipList');
        
        if (!ipResults || ipResults.length === 0) {
            ipListDiv.innerHTML = '<div class="empty">无可用IP服务</div>';
            return;
        }
        
        let html = '<div class="ip-cards">';
        ipResults.forEach((item, index) => {
            const isSuccess = item.status === 'success';
            const statusClass = isSuccess ? 'ip-success' : 'ip-failed';
            const statusIcon = isSuccess ? 'check-circle' : 'times-circle';
            const ipDisplay = isSuccess ? item.ip : '获取失败';
            
            html += `
                <div class="ip-card ${statusClass}" data-ip="${item.ip || ''}" data-url="${item.url}" data-name="${item.name}" data-index="${index}">
                    <div class="ip-card-header">
                        <span class="ip-service-name">${item.name}</span>
                        <span class="ip-service-type ${item.type}">${item.type === 'builtin' ? '内置' : '自定义'}</span>
                    </div>
                    <div class="ip-card-body">
                        <i class="fas fa-${statusIcon}"></i>
                        <span class="ip-value">${ipDisplay}</span>
                    </div>
                    <div class="ip-card-footer">
                        <small class="ip-url" title="${item.url}">${item.url}</small>
                    </div>
                    ${isSuccess ? '<div class="ip-select-indicator"><i class="fas fa-check"></i></div>' : ''}
                </div>
            `;
        });
        html += '</div>';
        
        ipListDiv.innerHTML = html;
        
        // 绑定点击事件选择IP
        document.querySelectorAll('.ip-card.ip-success').forEach(card => {
            card.addEventListener('click', function() {
                document.querySelectorAll('.ip-card').forEach(c => c.classList.remove('selected'));
                this.classList.add('selected');
                const ip = this.getAttribute('data-ip');
                document.getElementById('selectedIp').textContent = ip;
                document.getElementById('currentSelectedIp').value = ip;
                updateResolveButtonState();
            });
        });
        
        // 自动选择第一个成功的IP
        const firstSuccess = document.querySelector('.ip-card.ip-success');
        if (firstSuccess) {
            firstSuccess.click();
        }
    }

    // 刷新IP按钮
    const refreshIpBtn = document.getElementById('refreshIp');
    if (refreshIpBtn) {
        refreshIpBtn.addEventListener('click', function() {
            const icon = this.querySelector('i');
            icon.classList.add('fa-spin');
            loadAllIps();
            setTimeout(() => {
                icon.classList.remove('fa-spin');
            }, 2000);
        });
    }

    // ==================== DDNS任务管理 ====================
    function loadDdnsTasks(secretId) {
        const taskListDiv = document.getElementById('ddnsTaskList');
        taskListDiv.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载中...</div>';
        
        fetch(`/api/dns/tasks/${encodeURIComponent(secretId)}`)
            .then(response => response.json())
            .then(data => {
                if (data.code === 200 && Array.isArray(data.data)) {
                    renderDdnsTasks(data.data);
                } else {
                    taskListDiv.innerHTML = '<div class="empty"><i class="fas fa-inbox"></i> 暂无DDNS任务</div>';
                }
            })
            .catch(error => {
                console.error('获取DDNS任务失败:', error);
                taskListDiv.innerHTML = '<div class="error"><i class="fas fa-exclamation-circle"></i> 加载失败</div>';
            });
    }

    function renderDdnsTasks(tasks) {
        const taskListDiv = document.getElementById('ddnsTaskList');
        
        if (!tasks || tasks.length === 0) {
            taskListDiv.innerHTML = '<div class="empty"><i class="fas fa-inbox"></i> 暂无DDNS任务</div>';
            return;
        }
        
        let html = '<div class="ddns-tasks">';
        tasks.forEach(task => {
            const statusClass = task.status === 'running' ? 'status-running' : 
                               (task.status === 'error' ? 'status-error' : 'status-stopped');
            const statusText = task.status === 'running' ? '运行中' : 
                              (task.status === 'error' ? '错误' : '已停止');
            const intervalText = formatInterval(task.interval);
            
            html += `
                <div class="ddns-task-card ${statusClass}" data-task-id="${task.id}">
                    <div class="task-header">
                        <span class="task-domain">${task.fullDomain}</span>
                        <span class="task-status ${statusClass}">${statusText}</span>
                    </div>
                    <div class="task-info">
                        <div class="task-info-item">
                            <i class="fas fa-clock"></i>
                            <span>间隔: ${intervalText}</span>
                        </div>
                        <div class="task-info-item">
                            <i class="fas fa-server"></i>
                            <span>服务: ${task.ipServiceName}</span>
                        </div>
                        <div class="task-info-item">
                            <i class="fas fa-network-wired"></i>
                            <span>IP: ${task.lastIp || '-'}</span>
                        </div>
                        <div class="task-info-item">
                            <i class="fas fa-history"></i>
                            <span>更新: ${task.lastUpdateTime || '-'}</span>
                        </div>
                    </div>
                    ${task.lastError ? `<div class="task-error"><i class="fas fa-exclamation-triangle"></i> ${task.lastError}</div>` : ''}
                    <div class="task-actions">
                        ${task.enabled ? 
                            `<button class="btn btn-sm btn-warning task-stop-btn" data-id="${task.id}"><i class="fas fa-stop"></i> 停止</button>` :
                            `<button class="btn btn-sm btn-success task-start-btn" data-id="${task.id}"><i class="fas fa-play"></i> 启动</button>`
                        }
                        <button class="btn btn-sm task-execute-btn" data-id="${task.id}"><i class="fas fa-sync"></i> 立即执行</button>
                        <button class="btn btn-sm btn-secondary task-edit-btn" data-id="${task.id}"><i class="fas fa-edit"></i> 编辑</button>
                        <button class="btn btn-sm btn-danger task-delete-btn" data-id="${task.id}"><i class="fas fa-trash"></i> 删除</button>
                    </div>
                </div>
            `;
        });
        html += '</div>';
        
        taskListDiv.innerHTML = html;
        
        // 绑定任务操作按钮事件
        bindTaskActions(tasks);
    }

    function formatInterval(seconds) {
        if (seconds < 60) return seconds + ' 秒';
        if (seconds < 3600) return Math.floor(seconds / 60) + ' 分钟';
        return Math.floor(seconds / 3600) + ' 小时';
    }

    function bindTaskActions(tasks) {
        // 启动按钮
        document.querySelectorAll('.task-start-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const taskId = btn.dataset.id;
                const task = tasks.find(t => t.id === taskId);
                addLog(`启动任务: ${task?.fullDomain || taskId}`, 'info');
                fetch(`/api/dns/tasks/${taskId}/start`, { method: 'POST' })
                    .then(r => r.json())
                    .then(data => {
                        if (data.code === 200) {
                            addLog(`任务已启动: ${task?.fullDomain || taskId}`, 'success');
                            loadDdnsTasks(document.getElementById('currentSecretId').value);
                        } else {
                            addLog(`启动失败: ${data.message}`, 'error');
                            alert('启动失败: ' + data.message);
                        }
                    });
            });
        });

        // 停止按钮
        document.querySelectorAll('.task-stop-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const taskId = btn.dataset.id;
                const task = tasks.find(t => t.id === taskId);
                addLog(`停止任务: ${task?.fullDomain || taskId}`, 'info');
                fetch(`/api/dns/tasks/${taskId}/stop`, { method: 'POST' })
                    .then(r => r.json())
                    .then(data => {
                        if (data.code === 200) {
                            addLog(`任务已停止: ${task?.fullDomain || taskId}`, 'success');
                            loadDdnsTasks(document.getElementById('currentSecretId').value);
                        } else {
                            addLog(`停止失败: ${data.message}`, 'error');
                            alert('停止失败: ' + data.message);
                        }
                    });
            });
        });

        // 立即执行按钮
        document.querySelectorAll('.task-execute-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const taskId = btn.dataset.id;
                const task = tasks.find(t => t.id === taskId);
                addLog(`手动执行: ${task?.fullDomain || taskId}`, 'info');
                btn.disabled = true;
                btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
                fetch(`/api/dns/tasks/${taskId}/execute`, { method: 'POST' })
                    .then(r => r.json())
                    .then(data => {
                        if (data.code === 200) {
                            addLog(`执行成功: ${task?.fullDomain} - ${data.data}`, 'success');
                        } else {
                            addLog(`执行失败: ${data.message}`, 'error');
                        }
                        loadDdnsTasks(document.getElementById('currentSecretId').value);
                    })
                    .finally(() => {
                        btn.disabled = false;
                        btn.innerHTML = '<i class="fas fa-sync"></i> 立即执行';
                    });
            });
        });

        // 编辑按钮
        document.querySelectorAll('.task-edit-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const taskId = btn.dataset.id;
                const task = tasks.find(t => t.id === taskId);
                if (task) {
                    openEditDdnsModal(task);
                }
            });
        });

        // 删除按钮
        document.querySelectorAll('.task-delete-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const taskId = btn.dataset.id;
                const task = tasks.find(t => t.id === taskId);
                if (confirm(`确定要删除 ${task?.fullDomain || ''} 的DDNS任务吗？\n同时会删除云端的DNS解析记录！`)) {
                    addLog(`删除任务: ${task?.fullDomain || taskId}`, 'warn');
                    fetch(`/api/dns/tasks/${taskId}`, { method: 'DELETE' })
                        .then(r => r.json())
                        .then(data => {
                            if (data.code === 200) {
                                addLog(`任务已删除: ${task?.fullDomain}，云端DNS记录已清除`, 'success');
                                loadDdnsTasks(document.getElementById('currentSecretId').value);
                            } else {
                                addLog(`删除失败: ${data.message}`, 'error');
                                alert('删除失败: ' + data.message);
                            }
                        });
                }
            });
        });
    }

    // ==================== 添加DDNS任务 ====================
    const addDdnsBtn = document.getElementById('addDdnsBtn');
    const addDdnsModal = document.getElementById('addDdnsModal');
    const confirmAddDdns = document.getElementById('confirmAddDdns');

    if (addDdnsBtn) {
        addDdnsBtn.addEventListener('click', () => {
            const domain = document.getElementById('selectedDomain').value;
            const subdomain = document.getElementById('subdomain').value.trim();
            
            if (!domain || !subdomain) {
                alert('请先选择根域名并输入子域名');
                return;
            }
            
            const fullDomain = subdomain === '@' ? domain : subdomain + '.' + domain;
            document.getElementById('ddnsFullDomain').textContent = fullDomain;
            addDdnsModal.style.display = 'flex';
        });
    }

    // 模态框关闭
    document.querySelectorAll('.modal .close-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.closest('.modal').style.display = 'none';
        });
    });

    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.style.display = 'none';
            }
        });
    });

    if (confirmAddDdns) {
        confirmAddDdns.addEventListener('click', () => {
            const domain = document.getElementById('selectedDomain').value;
            const subdomain = document.getElementById('subdomain').value.trim();
            const interval = document.getElementById('ddnsInterval').value;
            const ipServiceSelect = document.getElementById('ddnsIpService');
            const ipServiceUrl = ipServiceSelect.value;
            const ipServiceName = ipServiceSelect.options[ipServiceSelect.selectedIndex]?.dataset.name || '';
            
            const formData = new URLSearchParams();
            formData.append('provider', document.getElementById('currentProvider').value);
            formData.append('secretId', document.getElementById('currentSecretId').value);
            formData.append('secretKey', document.getElementById('currentSecretKey').value);
            formData.append('domain', domain);
            formData.append('subdomain', subdomain);
            formData.append('ipServiceUrl', ipServiceUrl);
            formData.append('ipServiceName', ipServiceName);
            formData.append('interval', interval);
            
            confirmAddDdns.disabled = true;
            confirmAddDdns.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 添加中...';
            
            fetch('/api/dns/tasks', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            })
            .then(r => r.json())
            .then(data => {
                if (data.code === 200) {
                    const fullDomain = subdomain === '@' ? domain : subdomain + '.' + domain;
                    addLog(`添加DDNS任务: ${fullDomain}`, 'success');
                    addDdnsModal.style.display = 'none';
                    loadDdnsTasks(document.getElementById('currentSecretId').value);
                } else {
                    addLog(`添加任务失败: ${data.message}`, 'error');
                    alert('添加失败: ' + data.message);
                }
            })
            .finally(() => {
                confirmAddDdns.disabled = false;
                confirmAddDdns.innerHTML = '<i class="fas fa-plus"></i> 添加任务';
            });
        });
    }

    // ==================== 编辑DDNS任务 ====================
    const editDdnsModal = document.getElementById('editDdnsModal');
    const confirmEditDdns = document.getElementById('confirmEditDdns');

    function openEditDdnsModal(task) {
        document.getElementById('editingTaskId').value = task.id;
        document.getElementById('editDdnsFullDomain').textContent = task.fullDomain;
        document.getElementById('editDdnsInterval').value = task.interval;
        
        // 设置IP服务选择
        const select = document.getElementById('editDdnsIpService');
        for (let i = 0; i < select.options.length; i++) {
            if (select.options[i].value === task.ipServiceUrl) {
                select.selectedIndex = i;
                break;
            }
        }
        
        editDdnsModal.style.display = 'flex';
    }

    if (confirmEditDdns) {
        confirmEditDdns.addEventListener('click', () => {
            const taskId = document.getElementById('editingTaskId').value;
            const interval = document.getElementById('editDdnsInterval').value;
            const ipServiceSelect = document.getElementById('editDdnsIpService');
            const ipServiceUrl = ipServiceSelect.value;
            const ipServiceName = ipServiceSelect.options[ipServiceSelect.selectedIndex]?.dataset.name || '';
            
            const formData = new URLSearchParams();
            formData.append('interval', interval);
            formData.append('ipServiceUrl', ipServiceUrl);
            formData.append('ipServiceName', ipServiceName);
            
            confirmEditDdns.disabled = true;
            confirmEditDdns.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 保存中...';
            
            fetch(`/api/dns/tasks/${taskId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            })
            .then(r => r.json())
            .then(data => {
                if (data.code === 200) {
                    const fullDomain = document.getElementById('editDdnsFullDomain').textContent;
                    addLog(`修改任务配置: ${fullDomain}`, 'success');
                    editDdnsModal.style.display = 'none';
                    loadDdnsTasks(document.getElementById('currentSecretId').value);
                } else {
                    addLog(`修改失败: ${data.message}`, 'error');
                    alert('保存失败: ' + data.message);
                }
            })
            .finally(() => {
                confirmEditDdns.disabled = false;
                confirmEditDdns.innerHTML = '<i class="fas fa-save"></i> 保存';
            });
        });
    }

    // ==================== 添加IP服务 ====================
    const addServiceBtn = document.getElementById('addServiceBtn');
    const addServiceModal = document.getElementById('addServiceModal');
    const confirmAddService = document.getElementById('confirmAddService');
    const newServiceUrl = document.getElementById('newServiceUrl');

    if (addServiceBtn) {
        addServiceBtn.addEventListener('click', () => {
            addServiceModal.style.display = 'flex';
            newServiceUrl.value = '';
            newServiceUrl.focus();
        });
    }

    if (confirmAddService) {
        confirmAddService.addEventListener('click', () => {
            const url = newServiceUrl.value.trim();
            if (!url) {
                alert('请输入服务URL');
                return;
            }
            
            if (!url.startsWith('http://') && !url.startsWith('https://')) {
                alert('URL必须以 http:// 或 https:// 开头');
                return;
            }
            
            confirmAddService.disabled = true;
            confirmAddService.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 添加中...';
            
            fetch('/api/dns/addIpService', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ url: url })
            })
            .then(response => response.json())
            .then(data => {
                if (data.code === 200) {
                    addLog(`添加IP服务: ${url}`, 'success');
                    addServiceModal.style.display = 'none';
                    loadAllIps();
                } else {
                    addLog(`添加IP服务失败: ${data.message || '未知错误'}`, 'error');
                    alert('添加失败: ' + (data.message || '未知错误'));
                }
            })
            .finally(() => {
                confirmAddService.disabled = false;
                confirmAddService.innerHTML = '<i class="fas fa-check"></i> 添加';
            });
        });
    }

    // ==================== 获取域名列表 ====================
    function loadDomainList(id, key) {
        const domainListDiv = document.getElementById('domainList');
        const provider = document.getElementById('currentProvider').value || '腾讯云';
        
        domainListDiv.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载中...</div>';
        document.getElementById('resolveInline').style.display = 'none';
        document.getElementById('selectedDomain').value = '';
        
        const formData = new URLSearchParams();
        formData.append('id', id);
        formData.append('key', key);
        formData.append('provider', provider);
        
        fetch('/api/dns/domainList', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data.code === 200 && Array.isArray(data.data)) {
                renderDomainList(data.data);
            } else {
                domainListDiv.innerHTML = '<div class="error"><i class="fas fa-exclamation-circle"></i> ' + (data.message || '获取失败') + '</div>';
            }
        })
        .catch(error => {
            console.error('获取域名列表失败:', error);
            domainListDiv.innerHTML = '<div class="error"><i class="fas fa-exclamation-circle"></i> 请求失败</div>';
        });
    }

    function renderDomainList(domains) {
        const domainListDiv = document.getElementById('domainList');
        
        if (!domains || domains.length === 0) {
            domainListDiv.innerHTML = '<div class="empty"><i class="fas fa-inbox"></i> 暂无域名</div>';
            return;
        }
        
        let html = '<div class="domain-tags">';
        domains.forEach(domain => {
            html += `<span class="domain-tag selectable" data-domain="${domain}"><i class="fas fa-globe"></i> ${domain}</span>`;
        });
        html += '</div>';
        
        domainListDiv.innerHTML = html;
        
        // 绑定域名点击选择事件
        document.querySelectorAll('.domain-tag.selectable').forEach(tag => {
            tag.addEventListener('click', function() {
                // 移除其他选中状态
                document.querySelectorAll('.domain-tag').forEach(t => t.classList.remove('selected'));
                // 添加选中状态
                this.classList.add('selected');
                // 保存选中的域名
                const domain = this.dataset.domain;
                document.getElementById('selectedDomain').value = domain;
                document.getElementById('domainSuffix').textContent = '.' + domain;
                // 显示子域名输入区域
                document.getElementById('resolveInline').style.display = 'flex';
                document.getElementById('subdomain').focus();
                updateResolveButtonState();
            });
        });
    }

    function updateResolveButtonState() {
        const domain = document.getElementById('selectedDomain').value;
        const subdomain = document.getElementById('subdomain').value.trim();
        const ip = document.getElementById('currentSelectedIp').value;
        const resolveBtn = document.getElementById('resolveBtn');
        const addDdnsBtn = document.getElementById('addDdnsBtn');
        
        resolveBtn.disabled = !(domain && ip && subdomain);
        addDdnsBtn.disabled = !(domain && subdomain);
    }

    // 监听子域名输入
    const subdomainInput = document.getElementById('subdomain');
    if (subdomainInput) {
        subdomainInput.addEventListener('input', updateResolveButtonState);
    }

    // ==================== 解析域名 ====================
    const resolveBtn = document.getElementById('resolveBtn');
    if (resolveBtn) {
        resolveBtn.addEventListener('click', function() {
            const domain = document.getElementById('selectedDomain').value;
            const subdomain = document.getElementById('subdomain').value.trim();
            const id = document.getElementById('currentSecretId').value;
            const key = document.getElementById('currentSecretKey').value;
            const ip = document.getElementById('currentSelectedIp').value;
            
            if (!domain) {
                showResult('error', '请先点击选择一个根域名');
                return;
            }
            
            if (!subdomain) {
                showResult('error', '请输入子域名');
                return;
            }
            
            if (!ip) {
                showResult('error', '请选择一个IP地址');
                return;
            }
            
            const fullDomain = subdomain === '@' ? domain : subdomain + '.' + domain;
            addLog(`解析域名: ${fullDomain} -> ${ip}`, 'info');
            
            this.disabled = true;
            this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 处理中...';
            
            const formData = new URLSearchParams();
            formData.append('id', id);
            formData.append('key', key);
            formData.append('domain', domain);
            formData.append('subdomain', subdomain);
            formData.append('ip', ip);
            
            fetch('/api/dns/createOrUpdateRecord', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (data.code === 200) {
                    addLog(`解析成功: ${fullDomain} -> ${ip}`, 'success');
                    showResult('success', data.data);
                } else {
                    addLog(`解析失败: ${data.message || '操作失败'}`, 'error');
                    showResult('error', data.message || '操作失败');
                }
            })
            .catch(error => {
                console.error('解析失败:', error);
                addLog(`解析请求失败: ${error.message}`, 'error');
                showResult('error', '请求失败: ' + error.message);
            })
            .finally(() => {
                this.disabled = false;
                this.innerHTML = '<i class="fas fa-check"></i> 解析到选中IP';
            });
        });
    }

    function showResult(type, message) {
        const resultDiv = document.getElementById('resolveResult');
        resultDiv.style.display = 'block';
        resultDiv.className = 'resolve-result ' + (type === 'success' ? 'result-success' : 'result-error');
        resultDiv.innerHTML = `<i class="fas fa-${type === 'success' ? 'check-circle' : 'times-circle'}"></i> ${message}`;
        
        setTimeout(() => {
            resultDiv.style.display = 'none';
        }, 5000);
    }

    // ==================== 日志面板 ====================
    let logCount = 0;
    const logPanel = document.getElementById('logPanel');
    const logBody = document.getElementById('logBody');
    const logMinBtn = document.getElementById('logMinBtn');
    const logBadge = document.getElementById('logBadge');
    const toggleLogBtn = document.getElementById('toggleLogBtn');
    const clearLogBtn = document.getElementById('clearLogBtn');

    // 添加日志
    function addLog(message, type = 'info') {
        const now = new Date();
        const time = now.toTimeString().slice(0, 8);
        const entry = document.createElement('div');
        entry.className = `log-entry log-${type}`;
        entry.innerHTML = `<span class="log-time">${time}</span> ${message}`;
        logBody.appendChild(entry);
        logBody.scrollTop = logBody.scrollHeight;
        
        // 如果面板最小化，增加计数
        if (logPanel.style.display === 'none') {
            logCount++;
            logBadge.textContent = logCount;
            logBadge.style.display = logCount > 0 ? 'block' : 'none';
        }
    }

    // 最小化/展开日志面板
    if (toggleLogBtn) {
        toggleLogBtn.addEventListener('click', () => {
            logPanel.style.display = 'none';
            logMinBtn.style.display = 'flex';
        });
    }

    if (logMinBtn) {
        logMinBtn.addEventListener('click', () => {
            logPanel.style.display = 'flex';
            logMinBtn.style.display = 'none';
            logCount = 0;
            logBadge.textContent = '0';
            logBadge.style.display = 'none';
        });
    }

    // 清空日志
    if (clearLogBtn) {
        clearLogBtn.addEventListener('click', () => {
            logBody.innerHTML = '';
            addLog('日志已清空', 'info');
        });
    }

    // 暴露日志函数供全局使用
    window.addLog = addLog;

    // ==================== 轮询后端日志 ====================
    let lastLogIndex = 0;
    
    function pollServerLogs() {
        fetch(`/api/dns/logs?fromIndex=${lastLogIndex}`)
            .then(r => r.json())
            .then(data => {
                if (data.code === 200 && data.data) {
                    const logs = data.data.logs || [];
                    logs.forEach(log => {
                        const time = log.time ? log.time.split(' ')[1] : '--:--:--';
                        const entry = document.createElement('div');
                        entry.className = `log-entry log-${log.type}`;
                        entry.innerHTML = `<span class="log-time">${time}</span> ${log.message}`;
                        logBody.appendChild(entry);
                        logBody.scrollTop = logBody.scrollHeight;
                        
                        // 如果面板最小化，增加计数
                        if (logPanel.style.display === 'none') {
                            logCount++;
                            logBadge.textContent = logCount;
                            logBadge.style.display = logCount > 0 ? 'block' : 'none';
                        }
                    });
                    lastLogIndex = data.data.total || lastLogIndex;
                }
            })
            .catch(err => console.error('获取日志失败:', err));
    }
    
    // 每5秒轮询一次后端日志
    setInterval(pollServerLogs, 5000);
    // 初始加载
    pollServerLogs();

    // ==================== 提示框自动隐藏 ====================
    const alertBox = document.getElementById('alertBox');
    if (alertBox && alertBox.textContent.trim()) {
        setTimeout(() => {
            alertBox.style.transition = 'opacity 0.5s';
            alertBox.style.opacity = '0';
            setTimeout(() => {
                alertBox.style.display = 'none';
            }, 500);
        }, 3000);
    }

    // ==================== 修改密码表单验证 ====================
    const changePasswordForm = document.getElementById('changePasswordForm');
    if (changePasswordForm) {
        changePasswordForm.addEventListener('submit', function(e) {
            const newPassword = document.getElementById('newPassword').value;
            const confirmPassword = document.getElementById('confirmPassword').value;
            
            if (newPassword !== confirmPassword) {
                e.preventDefault();
                alert('两次输入的新密码不一致，请重新输入');
                return false;
            }
            
            if (newPassword.length < 6) {
                e.preventDefault();
                alert('新密码长度不能少于6位');
                return false;
            }
            
            if (newPassword === '123456') {
                e.preventDefault();
                alert('新密码不能与初始密码相同');
                return false;
            }
            
            return true;
        });
    }
});

(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            fn();
        }
    }

    var KDS_INTERVAL_MS = 2000;

    /* Màn bếp làm việc theo đơn: mỗi thẻ là một đơn, chữ ký gộp mọi thứ hiện trên thẻ để
       biết khi nào phải vẽ lại. Thứ tự phải khớp data-sig do JSP dựng ra, không thì thẻ nào
       máy chủ dựng cũng bị coi là đã đổi và vẽ lại ngay lần hỏi đầu tiên. */
    function kdsSignature(o) {
        return [o.totalQuantity, o.itemCount, o.online, o.urgent, o.late,
                o.pickupLabel, o.openIssueCount].join('|');
    }

    function kdsUrgency(o) {
        if (o.late) { return 'late'; }
        if (o.urgent) { return 'urgent'; }
        return o.online ? 'online' : 'pos';
    }

    function kdsBuildCard(tpl, o, detailBase) {
        var node = tpl.content.firstElementChild.cloneNode(true);
        kdsFillCard(node, o, detailBase);
        return node;
    }

    function kdsFillItems(list, items, detailBase) {
        var tpl = document.getElementById('kds-item-template');
        list.textContent = '';
        (items || []).forEach(function (it) {
            var li = tpl.content.firstElementChild.cloneNode(true);
            li.querySelector('[data-field="itemQty"]').textContent = '×' + it.quantity;
            var link = li.querySelector('[data-field="itemName"]');
            link.textContent = it.name;
            link.href = detailBase + it.orderItemId;
            list.appendChild(li);
        });
    }

    function kdsFillCard(node, o, detailBase) {
        node.dataset.orderId = o.orderId;
        node.dataset.sig = kdsSignature(o);
        node.className = 'kds-card ' + kdsUrgency(o);

        var source = node.querySelector('[data-field="source"]');
        source.textContent = o.online ? 'Đặt trước' : 'Tại quầy';
        source.className = 'tag ' + (o.online ? 'tag-info' : 'tag-muted');

        node.querySelector('[data-field="qty"]').textContent = o.totalQuantity + ' phần';
        node.querySelector('[data-field="orderLabel"]').textContent = 'Đơn #' + o.orderId;
        node.querySelector('[data-field="itemCount"]').textContent = o.itemCount + ' món';

        var pickup = node.querySelector('[data-field="pickupLabel"]');
        pickup.textContent = o.pickupLabel;
        pickup.className = o.late ? 'tag tag-red' : (o.urgent ? 'tag tag-amber' : '');

        kdsFillItems(node.querySelector('[data-slot="items"]'), o.items, detailBase);

        var issueSlot = node.querySelector('[data-slot="issue"]');
        if (o.openIssueCount > 0) {
            issueSlot.hidden = false;
            issueSlot.querySelector('[data-field="issue"]').textContent =
                o.openIssueCount + ' sự cố đang mở';
        } else {
            issueSlot.hidden = true;
        }

        node.querySelector('[data-field="orderId"]').value = o.orderId;
    }

    function kdsRender(grid, tpl, orders, detailBase) {
        var wanted = orders.map(function (o) { return String(o.orderId); });

        Array.prototype.slice.call(grid.children).forEach(function (card) {
            if (wanted.indexOf(card.dataset.orderId) === -1) {
                grid.removeChild(card);
            }
        });

        orders.forEach(function (o) {
            var card = grid.querySelector('[data-order-id="' + o.orderId + '"]');
            if (!card) {
                grid.appendChild(kdsBuildCard(tpl, o, detailBase));
            } else if (card.dataset.sig !== kdsSignature(o)) {
                kdsFillCard(card, o, detailBase);
            }
        });

        var current = Array.prototype.map.call(grid.children, function (c) { return c.dataset.orderId; });
        if (current.join(',') !== wanted.join(',')) {
            wanted.forEach(function (id) {
                var card = grid.querySelector('[data-order-id="' + id + '"]');
                if (card) { grid.appendChild(card); }
            });
        }
    }

    function watchKdsQueue() {
        var watch = document.getElementById('kds-watch');
        if (!watch) {
            return;
        }
        var grid = document.getElementById('kds-grid');
        var tpl = document.getElementById('kds-card-template');
        var emptyBox = document.getElementById('kds-empty');
        var offline = document.getElementById('kds-offline');
        var stale = document.getElementById('kds-stale');
        var reload = document.getElementById('kds-reload');
        var queuePager = document.querySelector('#kds-grid ~ nav.pager');
        if (!grid || !tpl) {
            return;
        }

        var endpoint = watch.dataset.endpoint;
        var detailBase = watch.dataset.detailBase;
        var renderedMyTasks = Number(watch.dataset.renderedMytasks);
        var renderedHandover = Number(watch.dataset.renderedHandover);
        /* Máy chủ chỉ dựng đúng một trang hàng chờ. API trả về cả danh sách nên trình duyệt
           phải cắt lại theo đúng trang đang xem, không thì đổi trang xong lại thấy đủ món. */
        var queuePage = Number(watch.dataset.queuePage) || 1;
        var queueSize = Number(watch.dataset.queueSize) || 0;
        var renderedQueue = Number(watch.dataset.renderedQueue) || 0;
        var misses = 0;

        if (reload) {
            reload.addEventListener('click', function () { window.location.reload(); });
        }

        function queuePages(total) {
            return queueSize > 0 ? Math.max(1, Math.ceil(total / queueSize)) : 1;
        }

        function setCount(id, value) {
            var el = document.getElementById(id);
            if (el) { el.textContent = value; }
        }

        function poll() {
            fetch(endpoint, { headers: { 'Accept': 'application/json' } })
                .then(function (r) {
                    if (!r.ok) { throw new Error('HTTP ' + r.status); }
                    return r.json();
                })
                .then(function (data) {
                    misses = 0;
                    if (offline) { offline.hidden = true; }

                    var all = data.queue || [];
                    var from = queueSize > 0 ? (queuePage - 1) * queueSize : 0;
                    var shown = queueSize > 0 ? all.slice(from, from + queueSize) : all;
                    /* Món trôi hết khỏi trang đang xem thì thanh phân trang cũng đã cũ:
                       báo người dùng tải lại chứ đừng nói dối là hết món. */
                    var outOfRange = all.length > 0 && shown.length === 0;
                    /* Thẻ món thì tự cập nhật được, còn thanh chuyển trang do máy chủ dựng nên
                       không. Chỉ nhắc tải lại khi số trang đổi thật — hàng chờ nhích vài món
                       trong cùng một trang mà cũng nhắc thì thành phiền, bếp sẽ bỏ qua. */
                    var pagerStale = outOfRange || queuePages(all.length) !== queuePages(renderedQueue);

                    kdsRender(grid, tpl, shown, detailBase);

                    grid.hidden = shown.length === 0;
                    if (emptyBox) { emptyBox.hidden = shown.length !== 0 || outOfRange; }
                    /* Hết sạch hàng chờ thì giấu luôn thanh chuyển trang: để lại một dòng
                       "đang xem 1–12 trong 12 đơn" ngay dưới khung báo hết món thì đọc vào
                       không biết tin bên nào. */
                    if (queuePager) { queuePager.hidden = all.length === 0; }

                    setCount('kds-mytasks-count', data.myOrderCount);
                    setCount('kds-handover-count', data.handoverCount);
                    setCount('kds-kpi-mytasks', data.myOrderCount);
                    setCount('kds-kpi-handover', data.handoverCount);
                    setCount('kds-kpi-queue', data.queueCount);
                    setCount('kds-kpi-issues', data.openIssueCount);

                    if (stale) {
                        stale.hidden = data.myOrderCount === renderedMyTasks
                                    && data.handoverCount === renderedHandover
                                    && !pagerStale;
                    }
                })
                .catch(function () {
                    misses++;
                    if (offline && misses >= 3) { offline.hidden = false; }
                });
        }

        poll();
        setInterval(poll, KDS_INTERVAL_MS);
    }

    var TRACK_INTERVAL_MS = 10000;
    var TRACK_STEPS = ['PENDING_PAYMENT', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED'];
    var TRACK_RELOAD_ON = ['READY', 'COMPLETED', 'CANCELLED', 'EXPIRED'];

    function trackUpdateSteps(status) {
        var steps = document.querySelectorAll('.steps .step');
        var idx = TRACK_STEPS.indexOf(status);
        if (idx === -1 || !steps.length) {
            return;
        }
        Array.prototype.forEach.call(steps, function (el, i) {
            var state = '';
            if (i < idx) {
                state = 'done';
            } else if (i === idx) {
                state = (idx === TRACK_STEPS.length - 1) ? 'done' : 'current';
            }
            el.className = 'step ' + state;
            if (state === 'current') {
                el.setAttribute('aria-current', 'step');
            } else {
                el.removeAttribute('aria-current');
            }
        });
    }

    function watchOrderStatus() {
        var watch = document.getElementById('order-watch');
        if (!watch) {
            return;
        }
        var endpoint = watch.dataset.endpoint;
        var current = watch.dataset.status;
        var badge = document.getElementById('order-status-badge');

        setInterval(function () {
            fetch(endpoint, { headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (data) {
                    if (!data || data.status === current) {
                        return;
                    }
                    if (TRACK_RELOAD_ON.indexOf(data.status) !== -1) {
                        location.reload();
                        return;
                    }
                    current = data.status;
                    if (badge) {
                        badge.textContent = data.statusLabel;
                        badge.className = data.statusClass;
                    }
                    trackUpdateSteps(data.status);
                })
                .catch(function () { });
        }, TRACK_INTERVAL_MS);
    }

    function guardDoubleSubmit() {
        document.addEventListener('submit', function (e) {
            var form = e.target;
            if (form.getAttribute('method') === 'dialog') {
                return;
            }
            if (form.dataset.submitting === 'yes') {
                e.preventDefault();
                return;
            }
            form.dataset.submitting = 'yes';

            var buttons = form.querySelectorAll('button[type="submit"], button:not([type])');
            setTimeout(function () {
                Array.prototype.forEach.call(buttons, function (b) {
                    b.disabled = true;
                    if (!b.dataset.keepLabel) {
                        b.dataset.originalLabel = b.textContent;
                        b.textContent = 'Đang xử lý…';
                    }
                });
            }, 0);
        });

        window.addEventListener('pageshow', function (e) {
            if (!e.persisted) {
                return;
            }
            Array.prototype.forEach.call(document.querySelectorAll('form[data-submitting]'), function (form) {
                delete form.dataset.submitting;
                Array.prototype.forEach.call(form.querySelectorAll('button[disabled]'), function (b) {
                    b.disabled = false;
                    if (b.dataset.originalLabel) { b.textContent = b.dataset.originalLabel; }
                });
            });
        });
    }

    function bindAutoSubmit() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-autosubmit]'), function (el) {
            el.addEventListener('change', function () {
                var form = el.form;
                if (!form) {
                    return;
                }
                if (form.requestSubmit) {
                    form.requestSubmit();
                } else {
                    form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
                    form.submit();
                }
            });
        });
    }

    var LIVE_SEARCH_DELAY_MS = 260;

    function liveSearchUrl(form) {
        var params = [];
        Array.prototype.forEach.call(form.elements, function (el) {
            if (!el.name || el.disabled || el.type === 'submit' || el.type === 'button') {
                return;
            }
            if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) {
                return;
            }
            if (el.value === '') {
                return;
            }
            params.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));
        });
        return form.action + (params.length ? '?' + params.join('&') : '');
    }

    function bindLiveSearch() {
        if (!window.fetch || !window.DOMParser || !window.history || !history.replaceState) {
            return;
        }

        Array.prototype.forEach.call(document.querySelectorAll('form[data-live-search]'), function (form) {
            var selector = form.getAttribute('data-live-search');
            var region = document.querySelector(selector);
            if (!region) {
                return;
            }

            var timer = null;
            var seq = 0;
            var lastUrl = liveSearchUrl(form);

            function done() {
                region.removeAttribute('data-live-busy');
                region.removeAttribute('aria-busy');
            }

            function run() {
                var url = liveSearchUrl(form);
                if (url === lastUrl) {
                    return;
                }
                lastUrl = url;

                var mine = ++seq;
                region.setAttribute('data-live-busy', '');
                region.setAttribute('aria-busy', 'true');

                fetch(url, { credentials: 'same-origin' })
                    .then(function (r) {
                        if (!r.ok) { throw new Error('HTTP ' + r.status); }
                        return r.text();
                    })
                    .then(function (html) {
                        if (mine !== seq) {
                            return;
                        }
                        var fresh = new DOMParser()
                            .parseFromString(html, 'text/html')
                            .querySelector(selector);

                        if (fresh) {
                            region.innerHTML = fresh.innerHTML;
                            history.replaceState(null, '', url);
                        } else {
                            lastUrl = '';
                        }
                        done();
                    })
                    .catch(function () {
                        if (mine !== seq) {
                            return;
                        }
                        lastUrl = '';
                        done();
                    });
            }

            form.addEventListener('input', function (e) {
                if (!e.target.name) {
                    return;
                }
                window.clearTimeout(timer);
                timer = window.setTimeout(run, LIVE_SEARCH_DELAY_MS);
            });

            form.addEventListener('change', function (e) {
                if (!e.target.name || e.target.type === 'search' || e.target.type === 'text') {
                    return;
                }
                window.clearTimeout(timer);
                run();
            });

            form.addEventListener('submit', function (e) {
                e.preventDefault();
                e.stopPropagation();
                window.clearTimeout(timer);
                run();
            });
        });
    }

    function bindNavToggle() {
        var btn = document.getElementById('nav-toggle');
        var nav = document.getElementById('main-nav');
        if (!btn || !nav) {
            return;
        }
        btn.hidden = false;

        function setOpen(open) {
            btn.setAttribute('aria-expanded', String(open));
            nav.classList.toggle('open', open);
        }

        btn.addEventListener('click', function () {
            setOpen(btn.getAttribute('aria-expanded') !== 'true');
        });

        nav.addEventListener('click', function (e) {
            if (e.target.closest('a')) {
                setOpen(false);
            }
        });

        document.addEventListener('click', function (e) {
            if (!nav.contains(e.target) && !btn.contains(e.target)) {
                setOpen(false);
            }
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && btn.getAttribute('aria-expanded') === 'true') {
                setOpen(false);
                btn.focus();
            }
        });

        window.addEventListener('resize', function () {
            if (window.innerWidth > 720) {
                setOpen(false);
            }
        });
    }

    function bindHeaderShadow() {
        var header = document.getElementById('app-header');
        if (!header) {
            return;
        }
        function sync() {
            header.classList.toggle('scrolled', window.scrollY > 4);
        }
        sync();
        window.addEventListener('scroll', sync, { passive: true });
    }

    function bindImageFallback() {
        document.addEventListener('error', function (e) {
            var img = e.target;
            if (!img || img.tagName !== 'IMG' || !img.classList.contains('thumb-img')) {
                return;
            }
            var box = document.createElement('div');
            box.className = img.className.replace('thumb-img', '').replace(/\s+/g, ' ').trim();
            box.setAttribute('aria-hidden', 'true');
            box.textContent = img.dataset.fallback || '🍔';
            img.parentNode.replaceChild(box, img);
        }, true);
    }

    function resubmit(form, submitter) {
        form.dataset.confirmed = 'yes';
        if (form.requestSubmit) {
            /* Gửi lại kèm đúng nút đã bấm, không thì biểu mẫu nào phân biệt việc
               theo name/value của nút sẽ mất mất thông tin đó sau khi xác nhận. */
            form.requestSubmit(submitter && submitter.form === form ? submitter : undefined);
        } else {
            form.submit();
        }
    }

    function bindConfirm() {
        var dlg = document.getElementById('confirm-dialog');
        var supported = dlg && typeof dlg.showModal === 'function';
        var okBtn = dlg && dlg.querySelector('[data-field="ok"]');

        document.addEventListener('submit', function (e) {
            var form = e.target;
            var message = form.dataset.confirm;
            if (!message || form.dataset.confirmed === 'yes') {
                return;
            }
            e.preventDefault();
            e.stopPropagation();

            var submitter = e.submitter;
            if (!supported) {
                if (window.confirm(message)) { resubmit(form, submitter); }
                return;
            }

            dlg.querySelector('[data-field="message"]').textContent = message;
            if (okBtn) {
                okBtn.textContent = form.dataset.confirmOk || 'Đồng ý';
                /* Nút trong trang màu đỏ thì nút đồng ý cũng đỏ: người dùng nhìn hộp thoại
                   là biết ngay việc sắp làm là xoá hay huỷ, chứ không phải thao tác thường. */
                var danger = form.dataset.confirmTone === 'danger'
                    || (submitter && submitter.classList.contains('btn-danger'));
                okBtn.className = 'btn touch ' + (danger ? 'btn-danger' : 'btn-primary');
            }
            dlg.returnValue = '';
            dlg.showModal();

            dlg.addEventListener('close', function handler() {
                dlg.removeEventListener('close', handler);
                if (dlg.returnValue === 'ok') { resubmit(form, submitter); }
            });
        }, true);
    }

    function bindUrlPreview() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-preview]'), function (input) {
            var img = document.getElementById(input.dataset.preview);
            var msg = document.getElementById(input.dataset.preview + 'Msg');
            if (!img) {
                return;
            }

            img.addEventListener('error', function () {
                img.hidden = true;
                if (msg) { msg.hidden = false; }
            });

            function refresh() {
                var url = input.value.trim();
                if (msg) { msg.hidden = true; }
                if (!url) {
                    img.hidden = true;
                    img.removeAttribute('src');
                    return;
                }
                img.hidden = false;
                img.src = url;
            }

            input.addEventListener('change', refresh);
            input.addEventListener('blur', refresh);
            refresh();
        });
    }

    function formatDong(amount) {
        return Math.round(amount).toLocaleString('vi-VN') + ' đ';
    }

    function bindCartQuantity() {
        var rows = document.querySelectorAll('[data-cart-line]');
        if (!rows.length) {
            return;
        }
        var totals = document.querySelectorAll('[data-cart-total]');

        /* Nhân tạm ở trình duyệt để tiền nhảy ngay khi người dùng đổi số lượng.
           Ô số lượng có data-autosubmit nên máy chủ vẫn lưu và vẽ lại con số
           chính thức ngay sau đó. */
        function refresh() {
            var sum = 0;
            Array.prototype.forEach.call(rows, function (row) {
                var input = row.querySelector('.qty-input');
                var output = row.querySelector('[data-line-total]');
                var price = parseFloat(row.dataset.unitPrice) || 0;
                var qty = parseInt(input ? input.value : '', 10);
                if (isNaN(qty) || qty < 0) {
                    /* Ô đang trống hoặc đang gõ dở: giữ nguyên số lượng cũ. */
                    qty = parseInt(input ? input.defaultValue : '', 10) || 0;
                }
                var line = price * qty;
                if (output) { output.textContent = formatDong(line); }
                sum += line;
            });
            Array.prototype.forEach.call(totals, function (el) {
                el.textContent = formatDong(sum);
            });
        }

        Array.prototype.forEach.call(rows, function (row) {
            var input = row.querySelector('.qty-input');
            if (input) { input.addEventListener('input', refresh); }
        });
    }

    var PAY_INTERVAL_MS = 5000;

    function watchPaymentStatus() {
        var watch = document.getElementById('payment-watch');
        if (!watch) {
            return;
        }
        var endpoint = watch.dataset.endpoint;
        var target = watch.dataset.redirect;

        setInterval(function () {
            fetch(endpoint, { headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (data) {
                    if (data && data.status && data.status !== 'PENDING_PAYMENT') {
                        location.href = target;
                    }
                })
                .catch(function () { });
        }, PAY_INTERVAL_MS);
    }

    /* Trang quầy: hỏi lại vài giây một lần xem cổng đã báo tiền về chưa. Thấy rồi thì tải lại
       trang để thu ngân nhìn thấy đúng trạng thái do máy chủ dựng ra — cố tình KHÔNG tự bấm
       Xong thay người: đưa đơn xuống bếp là quyết định của thu ngân đang đứng trước khách. */
    function watchPosPayment() {
        var watch = document.getElementById('pos-payment-watch');
        if (!watch) {
            return;
        }
        var endpoint = watch.dataset.endpoint;

        var timer = setInterval(function () {
            fetch(endpoint, { headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (data) {
                    if (data && data.paid) {
                        clearInterval(timer);
                        location.reload();
                    }
                })
                .catch(function () { });
        }, PAY_INTERVAL_MS);
    }

    function bindPrint() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-print]'), function (btn) {
            btn.addEventListener('click', function () { window.print(); });
        });
    }

    /* Kiểm tra dữ liệu nhập ngay trên trình duyệt. Các luật dưới đây phản chiếu
       ValidationUtil phía máy chủ — máy chủ vẫn là nơi quyết định cuối cùng. */

    var PW_MIN_LENGTH = 8;
    var PW_MAX_BYTES = 72;
    var EMAIL_RE = /^[\w.+-]+@[\w-]+\.[\w.-]+$/;
    var PHONE_RE = /^0\d{9,10}$/;
    var COMMON_PASSWORDS = [
        '12345678', '123456789', '1234567890', 'password', 'password1', 'password123',
        'qwerty123', 'abc12345', 'iloveyou', 'matkhau1', 'matkhau123', 'admin123',
        'fastfood', 'fastfood1', '11111111', '00000000', '1qaz2wsx', 'letmein1'];

    function unicodeRe(property, fallback) {
        try {
            return new RegExp('\\p{' + property + '}', 'u');
        } catch (e) {
            return fallback;
        }
    }

    var LETTER_RE = unicodeRe('L', /[a-zA-Z]/);
    var DIGIT_RE = unicodeRe('Nd', /[0-9]/);

    function byteLength(value) {
        if (window.TextEncoder) {
            return new TextEncoder().encode(value).length;
        }
        return unescape(encodeURIComponent(value)).length;
    }

    function passwordChecks(value) {
        return {
            len: value.length >= PW_MIN_LENGTH,
            letter: LETTER_RE.test(value),
            digit: DIGIT_RE.test(value)
        };
    }

    function passwordError(value) {
        var checks = passwordChecks(value);
        if (!checks.len) {
            return 'Mật khẩu phải có ít nhất ' + PW_MIN_LENGTH + ' ký tự.';
        }
        if (byteLength(value) > PW_MAX_BYTES) {
            return 'Mật khẩu quá dài. Vui lòng dùng tối đa ' + PW_MAX_BYTES + ' ký tự.';
        }
        if (!value.trim()) {
            return 'Mật khẩu không được chỉ gồm khoảng trắng.';
        }
        if (value !== value.trim()) {
            return 'Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.';
        }
        if (!checks.letter || !checks.digit) {
            return 'Mật khẩu phải có cả chữ và số.';
        }
        if (COMMON_PASSWORDS.indexOf(value.toLowerCase()) !== -1) {
            return 'Mật khẩu này quá phổ biến, vui lòng chọn mật khẩu khác.';
        }
        return null;
    }

    function fieldError(input) {
        var raw = input.value;
        var value = raw.trim();
        var rules = (input.dataset.validate || '').split(/\s+/).filter(Boolean);
        var label = input.dataset.label || 'thông tin';

        if (!value) {
            return input.required ? 'Vui lòng nhập ' + label + '.' : null;
        }

        for (var i = 0; i < rules.length; i++) {
            var rule = rules[i];
            if (rule === 'email' && !EMAIL_RE.test(value)) {
                return 'Địa chỉ email không hợp lệ.';
            }
            if (rule === 'phone' && !PHONE_RE.test(value)) {
                return 'Số điện thoại phải gồm 10 hoặc 11 chữ số và bắt đầu bằng 0.';
            }
            if (rule === 'password') {
                var pwError = passwordError(raw);
                if (pwError) { return pwError; }
            }
            if (rule.indexOf('match:') === 0) {
                var source = document.getElementById(rule.slice('match:'.length));
                if (source && source.value !== raw) {
                    return input.dataset.mismatch || 'Giá trị nhập lại không khớp.';
                }
            }
        }
        return null;
    }

    function showFieldError(input, error) {
        var field = input.closest('.field');
        if (!field) {
            return;
        }
        var msg = field.querySelector('.field-msg');
        field.classList.toggle('has-error', Boolean(error));
        if (error) {
            input.setAttribute('aria-invalid', 'true');
        } else {
            input.removeAttribute('aria-invalid');
        }
        if (msg) {
            msg.textContent = error || '';
            msg.hidden = !error;
        }
    }

    function refreshPasswordChecks(input) {
        var list = document.querySelector('[data-pw-checks="' + input.id + '"]');
        if (!list) {
            return;
        }
        var checks = passwordChecks(input.value);
        Array.prototype.forEach.call(list.querySelectorAll('[data-check]'), function (item) {
            var passed = Boolean(checks[item.dataset.check]);
            item.classList.toggle('ok', passed);
            item.dataset.state = passed ? 'ok' : 'todo';
        });
    }

    function bindFieldValidation() {
        var inputs = document.querySelectorAll('[data-validate]');
        if (!inputs.length) {
            return;
        }

        function revalidate(input, force) {
            if (force || input.dataset.touched === 'yes') {
                showFieldError(input, fieldError(input));
            }
        }

        Array.prototype.forEach.call(inputs, function (input) {
            /* Chỉ nhắc lỗi sau khi người dùng rời ô lần đầu, tránh báo đỏ ngay khi
               họ mới gõ được vài ký tự. */
            input.addEventListener('blur', function () {
                input.dataset.touched = 'yes';
                showFieldError(input, fieldError(input));
            });

            input.addEventListener('input', function () {
                refreshPasswordChecks(input);
                revalidate(input, false);

                /* Ô "nhập lại" phải được chấm lại khi ô gốc đổi. */
                Array.prototype.forEach.call(
                    document.querySelectorAll('[data-validate*="match:' + input.id + '"]'),
                    function (mirror) { revalidate(mirror, false); });
            });

            refreshPasswordChecks(input);
        });

        var forms = [];
        Array.prototype.forEach.call(inputs, function (input) {
            if (input.form && forms.indexOf(input.form) === -1) {
                forms.push(input.form);
            }
        });
        /* Tắt bong bóng lỗi mặc định của trình duyệt để dùng thông báo tiếng Việt
           của chúng ta. Nếu JavaScript không chạy, trình duyệt vẫn tự kiểm tra. */
        forms.forEach(function (form) { form.noValidate = true; });

        document.addEventListener('submit', function (e) {
            var form = e.target;
            if (forms.indexOf(form) === -1) {
                return;
            }
            var invalid = null;
            Array.prototype.forEach.call(form.querySelectorAll('[data-validate]'), function (input) {
                input.dataset.touched = 'yes';
                var error = fieldError(input);
                showFieldError(input, error);
                if (error && !invalid) { invalid = input; }
            });
            if (invalid) {
                /* Chặn hẳn để guardDoubleSubmit không khoá nút và bindConfirm
                   không mở hộp xác nhận cho một biểu mẫu còn lỗi. */
                e.preventDefault();
                e.stopImmediatePropagation();
                invalid.focus();
            }
        }, true);
    }

    /* Mở biểu mẫu sửa kế hoạch chuẩn bị ngay trong trang. Trước đây nút Sửa là một
       liên kết GET có editPrep nên trình duyệt phải tải lại toàn bộ KDS chỉ để điền form. */
    function bindPrepInlineEdit() {
        var panel = document.getElementById('prep-edit-panel');
        var form = document.getElementById('prep-edit-form');
        if (!panel || !form) {
            return;
        }

        var idInput = document.getElementById('prep-edit-id');
        var product = document.getElementById('prep-edit-product');
        var planned = document.getElementById('prep-edit-planned');
        var done = document.getElementById('prep-edit-done');
        var note = document.getElementById('prep-edit-note');

        function closeEditor() {
            panel.hidden = true;
            form.reset();
        }

        document.addEventListener('click', function (e) {
            var editButton = e.target.closest('[data-prep-edit]');
            if (editButton) {
                idInput.value = editButton.dataset.prepId || '';
                product.textContent = editButton.dataset.prepProduct || '';
                planned.value = editButton.dataset.prepPlanned || '';
                done.value = editButton.dataset.prepDone || '0';
                note.value = editButton.dataset.prepNote || '';
                panel.hidden = false;
                panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                window.setTimeout(function () { planned.focus(); }, 250);
                return;
            }

            if (e.target.closest('[data-prep-edit-cancel]')) {
                closeEditor();
            }
        });
    }

    ready(function () {
        watchKdsQueue();
        watchOrderStatus();
        watchPaymentStatus();
        watchPosPayment();
        guardDoubleSubmit();
        bindAutoSubmit();
        bindLiveSearch();
        bindNavToggle();
        bindHeaderShadow();
        bindImageFallback();
        bindFieldValidation();
        bindConfirm();
        bindUrlPreview();
        bindCartQuantity();
        bindPrint();
        bindPrepInlineEdit();
    });
})();

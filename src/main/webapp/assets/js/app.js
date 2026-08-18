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

    function kdsSignature(it) {
        return [it.quantity, it.online, it.urgent, it.late, it.pickupLabel, it.openIssueCount].join('|');
    }

    function kdsUrgency(it) {
        if (it.late) { return 'late'; }
        if (it.urgent) { return 'urgent'; }
        return it.online ? 'online' : 'pos';
    }

    function kdsBuildCard(tpl, it, detailBase) {
        var node = tpl.content.firstElementChild.cloneNode(true);
        kdsFillCard(node, it, detailBase);
        return node;
    }

    function kdsFillCard(node, it, detailBase) {
        node.dataset.itemId = it.orderItemId;
        node.dataset.sig = kdsSignature(it);
        node.className = 'kds-card ' + kdsUrgency(it);

        var source = node.querySelector('[data-field="source"]');
        source.textContent = it.online ? 'Đặt trước' : 'Tại quầy';
        source.className = 'tag ' + (it.online ? 'tag-info' : 'tag-muted');

        node.querySelector('[data-field="qty"]').textContent = '×' + it.quantity;
        node.querySelector('[data-field="productName"]').textContent = it.productName;
        node.querySelector('[data-field="orderLabel"]').textContent = 'Đơn #' + it.orderId;

        var pickup = node.querySelector('[data-field="pickupLabel"]');
        pickup.textContent = it.pickupLabel;
        pickup.className = it.late ? 'tag tag-red' : (it.urgent ? 'tag tag-amber' : '');

        var issueSlot = node.querySelector('[data-slot="issue"]');
        if (it.openIssueCount > 0) {
            issueSlot.hidden = false;
            issueSlot.querySelector('[data-field="issue"]').textContent =
                it.openIssueCount + ' sự cố đang mở';
        } else {
            issueSlot.hidden = true;
        }

        node.querySelector('[data-field="itemId"]').value = it.orderItemId;
        node.querySelector('[data-field="detailHref"]').href = detailBase + it.orderItemId;
    }

    function kdsRender(grid, tpl, items, detailBase) {
        var wanted = items.map(function (it) { return String(it.orderItemId); });

        Array.prototype.slice.call(grid.children).forEach(function (card) {
            if (wanted.indexOf(card.dataset.itemId) === -1) {
                grid.removeChild(card);
            }
        });

        items.forEach(function (it) {
            var card = grid.querySelector('[data-item-id="' + it.orderItemId + '"]');
            if (!card) {
                grid.appendChild(kdsBuildCard(tpl, it, detailBase));
            } else if (card.dataset.sig !== kdsSignature(it)) {
                kdsFillCard(card, it, detailBase);
            }
        });

        var current = Array.prototype.map.call(grid.children, function (c) { return c.dataset.itemId; });
        if (current.join(',') !== wanted.join(',')) {
            wanted.forEach(function (id) {
                var card = grid.querySelector('[data-item-id="' + id + '"]');
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
        if (!grid || !tpl) {
            return;
        }

        var endpoint = watch.dataset.endpoint;
        var detailBase = watch.dataset.detailBase;
        var renderedMyTasks = Number(watch.dataset.renderedMytasks);
        var renderedHandover = Number(watch.dataset.renderedHandover);
        var misses = 0;

        if (reload) {
            reload.addEventListener('click', function () { window.location.reload(); });
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

                    kdsRender(grid, tpl, data.queue || [], detailBase);

                    var isEmpty = !data.queue || data.queue.length === 0;
                    grid.hidden = isEmpty;
                    if (emptyBox) { emptyBox.hidden = !isEmpty; }

                    setCount('kds-mytasks-count', data.myTaskCount);
                    setCount('kds-handover-count', data.handoverCount);
                    setCount('kds-kpi-mytasks', data.myTaskCount);
                    setCount('kds-kpi-handover', data.handoverCount);
                    setCount('kds-kpi-queue', data.queueCount);
                    setCount('kds-kpi-issues', data.openIssueCount);

                    if (stale) {
                        stale.hidden = data.myTaskCount === renderedMyTasks
                                    && data.handoverCount === renderedHandover;
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

    function bindNavToggle() {
        var btn = document.getElementById('nav-toggle');
        var nav = document.getElementById('main-nav');
        if (!btn || !nav) {
            return;
        }
        btn.hidden = false;

        btn.addEventListener('click', function () {
            var open = btn.getAttribute('aria-expanded') === 'true';
            btn.setAttribute('aria-expanded', String(!open));
            nav.classList.toggle('open', !open);
        });
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

    function resubmit(form) {
        form.dataset.confirmed = 'yes';
        if (form.requestSubmit) {
            form.requestSubmit();
        } else {
            form.submit();
        }
    }

    function bindConfirm() {
        var dlg = document.getElementById('confirm-dialog');
        var supported = dlg && typeof dlg.showModal === 'function';

        document.addEventListener('submit', function (e) {
            var form = e.target;
            var message = form.dataset.confirm;
            if (!message || form.dataset.confirmed === 'yes') {
                return;
            }
            e.preventDefault();
            e.stopPropagation();

            if (!supported) {
                if (window.confirm(message)) { resubmit(form); }
                return;
            }

            dlg.querySelector('[data-field="message"]').textContent = message;
            dlg.returnValue = '';
            dlg.showModal();

            dlg.addEventListener('close', function handler() {
                dlg.removeEventListener('close', handler);
                if (dlg.returnValue === 'ok') { resubmit(form); }
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

    function bindChangeCalculator() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-change-form]'), function (form) {
            var input = form.querySelector('[data-change-input]');
            var output = form.querySelector('[data-change-output]');
            var total = parseFloat(form.dataset.total);
            if (!input || !output || isNaN(total)) {
                return;
            }

            function refresh() {
                var given = parseFloat(input.value);
                if (isNaN(given) || input.value.trim() === '') {
                    output.hidden = true;
                    output.textContent = '';
                    return;
                }
                output.hidden = false;
                if (given < total) {
                    output.className = 'total-line grand text-red';
                    output.textContent = 'Còn thiếu ' + formatDong(total - given);
                    return;
                }
                output.className = 'total-line grand';
                output.textContent = 'Thối lại ' + formatDong(given - total);
            }

            input.addEventListener('input', refresh);
            refresh();
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

    ready(function () {
        watchKdsQueue();
        watchOrderStatus();
        watchPaymentStatus();
        guardDoubleSubmit();
        bindAutoSubmit();
        bindNavToggle();
        bindImageFallback();
        bindFieldValidation();
        bindConfirm();
        bindUrlPreview();
        bindChangeCalculator();
        bindPrint();
    });
})();

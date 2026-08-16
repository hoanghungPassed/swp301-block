/* =============================================================================
   Fast Food Pre-order Pickup & POS — mã kịch bản dùng chung
   Viết tay, không phụ thuộc thư viện ngoài, để trang chạy được cả khi máy không có mạng.

   Trang JSP không chứa mã kịch bản; trang chỉ đánh dấu bằng thuộc tính data-* rồi tệp này
   tự tìm và gắn hành vi. Nhờ vậy sửa hành vi chỉ phải sửa một chỗ, và trang vẫn đọc được
   khi tắt JavaScript.
   ============================================================================= */

(function () {
    'use strict';

    /** Chạy hàm khi cây tài liệu đã dựng xong, kể cả khi tệp được nạp không kèm defer. */
    function ready(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            fn();
        }
    }

    /* ================================================================ màn hình bếp

       Yêu cầu NFR-04: món phải hiện trên màn hình bếp trong vòng 2 giây kể từ lúc được
       đưa xuống. Ba điều bản trước làm sai, sửa hết ở đây:

       1. Tải lại cả trang. Màn hình nháy trắng, mất vị trí cuộn, và nút đang định bấm thì
          nhảy đi chỗ khác — trong bếp đó là lỗi thật chứ không phải chuyện thẩm mỹ.
       2. So sánh bằng số đếm. Một món được người khác nhận đúng lúc một món mới xuống bếp
          thì số không đổi, và món mới không bao giờ hiện ra.
       3. Chu kỳ 5 giây, chậm hơn mức yêu cầu.

       Cách làm bây giờ: so từng món theo mã và theo "dấu vân" của nội dung, chỉ dựng lại
       đúng những thẻ có thay đổi. Thẻ không đổi thì không bị đụng tới, nên đầu bếp đang
       chạm vào một thẻ sẽ không bị thẻ đó biến mất dưới tay.
       ============================================================================= */

    var KDS_INTERVAL_MS = 2000;

    /** Dấu vân của một món: đổi một trong các giá trị này thì thẻ mới cần vẽ lại. */
    function kdsSignature(it) {
        return [it.quantity, it.online, it.urgent, it.late, it.pickupLabel, it.openIssueCount].join('|');
    }

    /** Mức độ gấp quyết định màu viền trái của thẻ. Trễ nặng hơn gấp, gấp nặng hơn kênh đặt. */
    function kdsUrgency(it) {
        if (it.late) { return 'late'; }
        if (it.urgent) { return 'urgent'; }
        return it.online ? 'online' : 'pos';
    }

    /*
      Dựng một thẻ từ khuôn <template> do trang cung cấp. Khuôn nằm trong JSP chứ không nằm
      ở đây: như vậy phần chữ và phần bố cục của thẻ chỉ có một bản duy nhất, tệp này chỉ
      lo việc điền dữ liệu vào các ô đã đánh dấu sẵn.

      Mọi giá trị đều gán bằng textContent, không bao giờ bằng innerHTML — tên món do người
      dùng nhập, nối thẳng vào HTML là mở đường cho chèn mã.
    */
    function kdsBuildCard(tpl, it, detailBase) {
        var node = tpl.content.firstElementChild.cloneNode(true);
        kdsFillCard(node, it, detailBase);
        return node;
    }

    /*
      Điền dữ liệu vào một thẻ đã có sẵn thay vì dựng thẻ mới.

      Quan trọng với thẻ đang hiển thị: nhãn giờ hẹn là thời gian tương đối ("còn 12 phút")
      nên cứ mỗi phút lại đổi. Nếu mỗi lần đổi mà thay cả thẻ thì nút "Nhận món này" bị dựng
      lại ngay dưới ngón tay đầu bếp — đúng cái lỗi mà bản tải lại cả trang mắc phải, chỉ
      nhỏ hơn. Sửa từng ô thì nút giữ nguyên, không mất tiêu điểm, không lỡ cú chạm.
    */
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

    /** Đưa lưới thẻ về đúng danh sách món hiện tại, đụng vào càng ít nút càng tốt. */
    function kdsRender(grid, tpl, items, detailBase) {
        var wanted = items.map(function (it) { return String(it.orderItemId); });

        // Bỏ thẻ của món không còn trong hàng chờ.
        Array.prototype.slice.call(grid.children).forEach(function (card) {
            if (wanted.indexOf(card.dataset.itemId) === -1) {
                grid.removeChild(card);
            }
        });

        // Thêm thẻ mới; thẻ đã có thì sửa tại chỗ, không thay nút.
        items.forEach(function (it) {
            var card = grid.querySelector('[data-item-id="' + it.orderItemId + '"]');
            if (!card) {
                grid.appendChild(kdsBuildCard(tpl, it, detailBase));
            } else if (card.dataset.sig !== kdsSignature(it)) {
                kdsFillCard(card, it, detailBase);
            }
        });

        // Xếp lại theo thứ tự ưu tiên máy chủ trả về — nhưng chỉ khi thứ tự thật sự khác,
        // vì di chuyển nút trong cây tài liệu có thể làm mất tiêu điểm bàn phím.
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
        // Số thẻ máy chủ đã vẽ ở hai khối "đang làm" và "chờ bàn giao". Cố định suốt lượt xem
        // trang này: đó chính là thứ để so xem những gì đang hiển thị có còn đúng không.
        var renderedMyTasks = Number(watch.dataset.renderedMytasks);
        var renderedHandover = Number(watch.dataset.renderedHandover);
        var misses = 0;

        if (reload) {
            reload.addEventListener('click', function () { window.location.reload(); });
        }

        /** Ghi một con số vào ô chỉ báo, bỏ qua nếu trang không có ô đó. */
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

                    // Chỉ cập nhật con số, không dựng lại hai khối phía trên: thẻ ở đó có nút
                    // gửi biểu mẫu, vẽ lại giữa chừng sẽ cướp mất cú bấm đang dở của đầu bếp.
                    setCount('kds-mytasks-count', data.myTaskCount);
                    setCount('kds-handover-count', data.handoverCount);
                    setCount('kds-kpi-mytasks', data.myTaskCount);
                    setCount('kds-kpi-handover', data.handoverCount);
                    setCount('kds-kpi-queue', data.queueCount);
                    setCount('kds-kpi-issues', data.openIssueCount);

                    // Con số đã đổi nhưng thẻ thì không — vì cố ý không vẽ lại. Nói ra chỗ
                    // lệch đó và mời tải lại, thay vì để đầu bếp nhìn một khối đã cũ mà
                    // tưởng là mới.
                    if (stale) {
                        stale.hidden = data.myTaskCount === renderedMyTasks
                                    && data.handoverCount === renderedHandover;
                    }
                })
                .catch(function () {
                    // Một lần hụt có thể chỉ là mạng chớp. Báo cho đầu bếp khi đã hụt liên
                    // tiếp — màn hình đứng im mà không ai biết còn nguy hiểm hơn là báo thừa.
                    misses++;
                    if (offline && misses >= 3) { offline.hidden = false; }
                });
        }

        poll();
        setInterval(poll, KDS_INTERVAL_MS);
    }

    /* ======================================================= trang theo dõi đơn hàng

       Khách đang chờ món không phải bấm tải lại. Đổi trạng thái trong cùng chặng chế biến
       thì cập nhật ngay tại chỗ; còn khi đơn sang trạng thái làm cả trang đổi khác hẳn
       (sẵn sàng, đã nhận, huỷ, hết hạn — lúc đó trang mọc thêm mã nhận hàng, mốc thời gian,
       khối cảnh báo) thì tải lại là đúng, vì vá từng mảnh sẽ dựng lại gần hết trang.
       ============================================================================= */

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
                // Bước cuối cùng đạt tới nghĩa là xong hẳn, không phải "đang ở đây".
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
                .catch(function () { /* mất mạng tạm thời thì thử lại ở lần sau */ });
        }, TRACK_INTERVAL_MS);
    }

    /* ============================================================== chống bấm trùng

       Thu ngân bấm "thu tiền" hai lần vì lần đầu không thấy gì phản hồi là chuyện thường.
       Phía máy chủ đã có khoá chống trùng nên dữ liệu không hỏng, nhưng người dùng vẫn cần
       thấy là máy đã nhận lệnh.

       Nút chỉ bị khoá ở nhịp sau (setTimeout 0): khoá ngay trong lúc đang xử lý sự kiện thì
       trình duyệt bỏ luôn tên và giá trị của nút khi gửi biểu mẫu.
       ============================================================================= */

    function guardDoubleSubmit() {
        document.addEventListener('submit', function (e) {
            var form = e.target;
            // Biểu mẫu bên trong <dialog> chỉ dùng để đóng hộp thoại, không gửi đi đâu cả.
            // Khoá nút của nó lại thì lần mở hộp thoại sau sẽ bấm không được nữa.
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

        // Quay lại bằng nút Back của trình duyệt có thể lấy trang từ bộ nhớ đệm, kèm theo
        // cả những nút đang bị khoá. Mở khoá lại để trang không kẹt cứng.
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

    /* ====================================================== ô chọn tự gửi biểu mẫu

       Thay cho onchange="this.form.submit()" viết thẳng trong trang. Dùng requestSubmit
       chứ không phải submit: submit() không phát ra sự kiện nào cả, nên phần chống bấm
       trùng ở trên sẽ không thấy gì và không chặn được lần gửi thứ hai.
       ============================================================================= */

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

    /* ========================================================== thu gọn thanh điều hướng

       Nút chỉ được hiện khi tệp này chạy được. Nếu trình duyệt tắt JavaScript thì nút nằm
       im dưới thuộc tính hidden và thanh điều hướng hiện đầy đủ như cũ — thà chiếm chỗ còn
       hơn để lại một cái nút bấm mãi không mở ra gì.
       ============================================================================= */

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

    /* ============================================================== ảnh lấy từ link ngoài

       Đường dẫn ảnh do quản trị viên nhập và trỏ ra máy chủ bên ngoài, nên sớm muộn cũng có
       cái chết hoặc chậm. Thay vì để trình duyệt vẽ ô ảnh vỡ, đổi về đúng nền giữ chỗ mà
       trang vẫn dùng cho món chưa có ảnh — nhìn không ra là có gì hỏng.

       Sự kiện lỗi của ảnh không nổi bọt lên, nên phải nghe ở pha bắt (tham số true).
       ============================================================================= */

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

    /* ================================================================= hộp xác nhận

       Thay cho confirm() của trình duyệt: hộp thoại gốc không đổi được chữ, không theo giao
       diện của trang, và trên vài trình duyệt còn kèm ô "chặn không cho trang này hỏi nữa" —
       tick nhầm một lần là từ đó huỷ đơn không hỏi lại câu nào.

       Trang chỉ cần ghi data-confirm="câu hỏi" lên thẻ form.
       ============================================================================= */

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

        // Pha bắt: chạy trước phần chống bấm trùng, để dừng hẳn lần gửi này lại trước khi
        // biểu mẫu bị đánh dấu là "đang gửi" và các nút bị khoá.
        document.addEventListener('submit', function (e) {
            var form = e.target;
            var message = form.dataset.confirm;
            if (!message || form.dataset.confirmed === 'yes') {
                return;
            }
            e.preventDefault();
            e.stopPropagation();

            // Trình duyệt quá cũ không có <dialog>: quay về hộp thoại gốc. Thà xấu còn hơn
            // để thao tác không hoàn tác được đi thẳng mà không hỏi câu nào.
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

    /* ========================================================== xem trước ảnh khi nhập

       Ô nhập đường dẫn ảnh ghi data-preview="id của thẻ img". Đường dẫn trỏ ra ngoài nên
       cách duy nhất biết nó sống hay chết là thử tải thật.
       ============================================================================= */

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

    /* ============================================================== tính tiền thối

       Thu ngân nhẩm tiền thối trong đầu là chỗ sai tiền dễ nhất trong cả ca, và cũng là chỗ
       khó phát hiện nhất: két lệch vài chục nghìn thì cuối ca mới biết, lúc đó không còn nhớ
       nổi đơn nào.

       Con số này KHÔNG gửi lên máy chủ và không được lưu: khoản thu luôn đúng bằng tổng đơn,
       còn tờ tiền khách đưa là chuyện xảy ra ở mặt quầy. Ô nhập vì vậy cố ý không có thuộc
       tính name. Tắt JavaScript thì ô nằm im như một ô trống và nút thu tiền vẫn bấm được.
       ============================================================================= */

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
                    // Thiếu tiền không phải lỗi chặn: khách có thể đưa làm hai lần. Chỉ nói ra
                    // còn thiếu bao nhiêu, và không đụng tới nút thu tiền.
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

    /* ================================================== trang chuyển khoản mã QR (SePay)

       Khác trang theo dõi đơn ở chỗ khách đang chờ MỘT sự kiện duy nhất: tiền vào tới nơi.
       Sự kiện ấy đi từ máy chủ SePay thẳng tới máy chủ của mình, không đi qua trình duyệt
       của khách, nên nếu không hỏi lại thì màn hình này đứng yên mãi mãi kể cả khi đơn đã
       được xác nhận xong. Hỏi dày hơn trang theo dõi đơn vì khách đang ngồi nhìn màn hình
       chờ, mỗi giây trôi qua ở đây đắt hơn nhiều.

       Đơn rời khỏi trạng thái chờ thanh toán là xong việc của trang này — dù rời sang đâu.
       Sang CONFIRMED thì tiền đã vào; sang EXPIRED hay CANCELLED thì mã QR trên màn hình
       không còn giá trị nữa và để khách quét tiếp là dẫn tới một lần chuyển tiền cho đơn đã
       chết. Cả hai đều phải rời trang, và trang theo dõi đơn nói rõ chuyện gì đã xảy ra.
       ============================================================================= */

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
                .catch(function () { /* mất mạng tạm thời thì thử lại ở lần sau */ });
        }, PAY_INTERVAL_MS);
    }

    /** Nút in. Để ở đây thay vì onclick="window.print()" viết thẳng trong trang. */
    function bindPrint() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-print]'), function (btn) {
            btn.addEventListener('click', function () { window.print(); });
        });
    }

    ready(function () {
        watchKdsQueue();
        watchOrderStatus();
        watchPaymentStatus();
        guardDoubleSubmit();
        bindAutoSubmit();
        bindNavToggle();
        bindImageFallback();
        bindConfirm();
        bindUrlPreview();
        bindChangeCalculator();
        bindPrint();
    });
})();

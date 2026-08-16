/*
  Kiểm thử giao diện trong trình duyệt thật.

  Bộ test bằng curl chỉ đọc được HTML máy chủ gửi ra; những thứ chỉ tồn tại sau khi
  JavaScript chạy — thẻ trên màn hình bếp được vẽ lại, nút mở menu, ảnh hỏng đổi về nền
  giữ chỗ, hộp xác nhận, chống bấm trùng — thì phải mở bằng trình duyệt mới thấy.
*/

const { chromium } = require('playwright');

const BASE = process.env.BASE || 'http://localhost:8081';
let pass = 0, fail = 0;
const failures = [];

function check(desc, condition) {
    if (condition) { pass++; console.log(`  \x1b[32mOK\x1b[0m   ${desc}`); }
    else { fail++; failures.push(desc); console.log(`  \x1b[31mFAIL\x1b[0m ${desc}`); }
}

async function login(page, email) {
    await page.goto(`${BASE}/login`);
    await page.fill('#email', email);
    await page.fill('#password', '123456');
    await Promise.all([page.waitForLoadState('networkidle'), page.click('button[type=submit]')]);
}

(async () => {
    const browser = await chromium.launch({ channel: 'chrome' });
    const errors = [];

    // ---------------------------------------------------------------- khách hàng
    {
        const ctx = await browser.newContext();
        const page = await ctx.newPage();
        page.on('pageerror', e => errors.push(`${page.url()} :: ${e.message}`));
        page.on('console', m => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(`${page.url()} :: ${m.text()}`); });

        console.log('════ A. Thực đơn (không đăng nhập) ════');
        await page.goto(`${BASE}/menu`, { waitUntil: 'networkidle' });

        check('Nút mở menu được JavaScript bật lên',
            await page.locator('#nav-toggle').count() === 1
            && await page.evaluate(() => !document.getElementById('nav-toggle').hidden));

        check('Tiêu đề trang đúng', (await page.title()) === 'Thực đơn · Fast Food');

        // Ảnh trỏ ra ngoài; nếu tải được thì vẫn là <img>, nếu hỏng thì app.js phải đổi
        // về nền giữ chỗ. Cả hai đều đúng — điều sai duy nhất là còn lại ô ảnh vỡ.
        const thumbs = await page.evaluate(() => {
            const cards = Array.from(document.querySelectorAll('.product'));
            return cards.map(c => {
                const img = c.querySelector('img.thumb-img');
                const box = c.querySelector('div.thumb');
                if (img) return img.naturalWidth > 0 ? 'anh-that' : 'anh-vo';
                return box ? 'nen-giu-cho' : 'khong-co-gi';
            });
        });
        check(`Mọi ô ảnh đều hiển thị được (${[...new Set(thumbs)].join(', ')})`,
            thumbs.length > 0 && !thumbs.includes('anh-vo') && !thumbs.includes('khong-co-gi'));

        console.log('\n════ B. Ảnh chết phải đổi về nền giữ chỗ ════');
        await page.route('**/placehold.co/**', r => r.abort());
        await page.goto(`${BASE}/menu`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        const afterAbort = await page.evaluate(() => ({
            imgs: document.querySelectorAll('img.thumb-img').length,
            boxes: document.querySelectorAll('div.thumb').length,
        }));
        check(`Ảnh hỏng bị thay hết bằng nền giữ chỗ (còn ${afterAbort.imgs} img, ${afterAbort.boxes} nền)`,
            afterAbort.imgs === 0 && afterAbort.boxes > 0);
        await page.unroute('**/placehold.co/**');

        console.log('\n════ C. Thu gọn menu trên màn hình nhỏ ════');
        await page.setViewportSize({ width: 375, height: 720 });
        await page.goto(`${BASE}/menu`, { waitUntil: 'networkidle' });
        const navHiddenAtFirst = await page.locator('#main-nav').isVisible();
        await page.click('#nav-toggle');
        const navShownAfterClick = await page.locator('#main-nav').isVisible();
        check('Màn hình hẹp: thanh điều hướng thu lại, bấm nút thì mở ra',
            navHiddenAtFirst === false && navShownAfterClick === true);
        check('Nút thu gọn báo đúng trạng thái cho trình đọc màn hình',
            await page.getAttribute('#nav-toggle', 'aria-expanded') === 'true');

        console.log('\n════ D. Trang không tràn ngang trên điện thoại ════');
        await login(page, 'customer1@gmail.com');
        await page.setViewportSize({ width: 375, height: 720 });
        for (const path of ['/menu', '/cart', '/order/history', '/profile']) {
            await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' });
            const overflow = await page.evaluate(() =>
                document.documentElement.scrollWidth - document.documentElement.clientWidth);
            check(`${path} không bị tràn ngang (thừa ${overflow}px)`, overflow <= 1);
        }

        await ctx.close();
    }

    // ---------------------------------------------------------------- bếp
    {
        const ctx = await browser.newContext();
        const page = await ctx.newPage();
        page.on('pageerror', e => errors.push(`KDS :: ${e.message}`));
        page.on('console', m => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(`KDS :: ${m.text()}`); });

        console.log('\n════ F. Màn hình bếp tự cập nhật ════');
        await login(page, 'kitchen1@fastfood.vn');
        await page.goto(`${BASE}/kitchen/queue`, { waitUntil: 'networkidle' });

        let polls = 0;
        page.on('response', r => { if (r.url().includes('/api/kds/queue')) polls++; });

        // Đánh dấu các nút hiện có rồi chờ qua vài nhịp hỏi. Nếu hàng chờ không đổi thì
        // đúng ra không nút nào bị dựng lại — đó chính là điều bản cũ làm sai khi nó
        // tải lại cả trang mỗi 5 giây.
        // Đánh dấu chính các NÚT, không phải thẻ: điều thật sự quan trọng là nút "Nhận món"
        // không bị dựng lại dưới ngón tay đầu bếp, kể cả khi nhãn giờ hẹn đếm lùi thay đổi.
        await page.evaluate(() => {
            document.querySelectorAll('#kds-grid .kds-card').forEach((c, i) => {
                c.dataset.mark = 'card' + i;
                const b = c.querySelector('button[type=submit]');
                if (b) { b.dataset.mark = 'btn' + i; }
            });
            window.__reloaded = false;
            window.addEventListener('beforeunload', () => { window.__reloaded = true; });
        });
        const before = await page.evaluate(() =>
            document.querySelectorAll('#kds-grid .kds-card button[type=submit]').length);
        await page.waitForTimeout(7000);

        const after = await page.evaluate(() => ({
            reloaded: window.__reloaded,
            cardsKept: document.querySelectorAll('#kds-grid .kds-card[data-mark]').length,
            buttonsKept: document.querySelectorAll('#kds-grid .kds-card button[data-mark]').length,
        }));

        check(`Hỏi máy chủ đúng nhịp 2 giây (${polls} lượt trong 7 giây)`, polls >= 3);
        check('Không tải lại cả trang', after.reloaded === false);
        check(`Thẻ không bị dựng lại (${after.cardsKept}/${before} giữ nguyên)`,
            before === 0 || after.cardsKept === before);
        check(`Nút nhận món không bị thay dưới tay (${after.buttonsKept}/${before} giữ nguyên)`,
            before === 0 || after.buttonsKept === before);
        check('Cảnh báo mất kết nối đang ẩn khi mạng bình thường',
            await page.evaluate(() => document.getElementById('kds-offline').hidden === true));

        console.log('\n════ G. Mất kết nối thì phải báo ════');
        await page.route('**/api/kds/queue', r => r.abort());
        await page.waitForTimeout(8000);
        check('Hụt liên tiếp thì hiện cảnh báo mất kết nối',
            await page.evaluate(() => document.getElementById('kds-offline').hidden === false));
        await page.unroute('**/api/kds/queue');
        await page.waitForTimeout(5000);
        check('Có mạng lại thì cảnh báo tự tắt',
            await page.evaluate(() => document.getElementById('kds-offline').hidden === true));

        console.log('\n════ H. Vẽ thẻ từ dữ liệu giả ════');
        // Ép máy chủ trả về một hàng chờ dựng sẵn để kiểm tra đúng phần vẽ thẻ, không phụ
        // thuộc vào việc lúc chạy test trong bếp có món hay không.
        await page.route('**/api/kds/queue', r => r.fulfill({
            status: 200,
            contentType: 'application/json;charset=UTF-8',
            body: JSON.stringify({
                queue: [
                    { orderItemId: 9001, orderId: 777, productName: 'Gà Rán <script>x</script>', quantity: 3,
                      online: true, urgent: false, late: true, pickupLabel: 'trễ 5 phút', openIssueCount: 2 },
                    { orderItemId: 9002, orderId: 778, productName: 'Khoai Tây Chiên', quantity: 1,
                      online: false, urgent: false, late: false, pickupLabel: 'Khách đang đợi tại quầy',
                      openIssueCount: 0 },
                ],
                queueCount: 2, myTaskCount: 4,
            }),
        }));
        await page.waitForTimeout(3000);

        const drawn = await page.evaluate(() => {
            const c = document.querySelector('#kds-grid .kds-card[data-item-id="9001"]');
            const d = document.querySelector('#kds-grid .kds-card[data-item-id="9002"]');
            if (!c || !d) return null;
            return {
                lateClass: c.className,
                name: c.querySelector('[data-field="productName"]').textContent,
                nameHtml: c.querySelector('[data-field="productName"]').innerHTML,
                qty: c.querySelector('[data-field="qty"]').textContent,
                issueShown: !c.querySelector('[data-slot="issue"]').hidden,
                issueText: c.querySelector('[data-field="issue"]').textContent,
                otherIssueHidden: d.querySelector('[data-slot="issue"]').hidden,
                itemIdInput: c.querySelector('[data-field="itemId"]').value,
                detailHref: c.querySelector('[data-field="detailHref"]').getAttribute('href'),
                posClass: d.className,
                taskCount: document.getElementById('kds-mytasks-count').textContent,
                gridShown: !document.getElementById('kds-grid').hidden,
            };
        });

        check('Vẽ được thẻ từ dữ liệu trả về', drawn !== null);
        if (drawn) {
            check('Món trễ được tô đúng mức ưu tiên', drawn.lateClass.includes('late'));
            check('Món tại quầy tô đúng loại', drawn.posClass.includes('pos'));
            check('Tên món hiện đúng', drawn.name === 'Gà Rán <script>x</script>');
            check('Tên món được thoát ký tự, không chèn được mã',
                !drawn.nameHtml.includes('<script>') && drawn.nameHtml.includes('&lt;script&gt;'));
            check('Số lượng hiện đúng', drawn.qty === '×3');
            check('Có sự cố thì hiện nhãn', drawn.issueShown && drawn.issueText === '2 sự cố đang mở');
            check('Không có sự cố thì ẩn nhãn', drawn.otherIssueHidden === true);
            check('Nút nhận món mang đúng mã món', drawn.itemIdInput === '9001');
            check('Liên kết chi tiết trỏ đúng món', drawn.detailHref.endsWith('/kitchen/item?id=9001'));
            check('Số việc của tôi được cập nhật', drawn.taskCount === '4');
            check('Lưới thẻ đang hiện', drawn.gridShown === true);
        }

        console.log('\n════ I. Hàng chờ rỗng ════');
        await page.unroute('**/api/kds/queue');
        await page.route('**/api/kds/queue', r => r.fulfill({
            status: 200, contentType: 'application/json;charset=UTF-8',
            body: JSON.stringify({ queue: [], queueCount: 0, myTaskCount: 0 }),
        }));
        await page.waitForTimeout(3000);
        const emptyState = await page.evaluate(() => ({
            gridHidden: document.getElementById('kds-grid').hidden,
            emptyShown: !document.getElementById('kds-empty').hidden,
            cards: document.querySelectorAll('#kds-grid .kds-card').length,
        }));
        check('Hàng chờ rỗng: ẩn lưới, hiện khối "không còn món"',
            emptyState.gridHidden === true && emptyState.emptyShown === true && emptyState.cards === 0);

        await ctx.close();
    }

    // ---------------------------------------------------------------- thu ngân
    {
        const ctx = await browser.newContext();
        const page = await ctx.newPage();
        page.on('pageerror', e => errors.push(`POS :: ${e.message}`));

        console.log('\n════ J. Chống bấm trùng ở quầy ════');
        await login(page, 'cashier1@fastfood.vn');
        await page.goto(`${BASE}/staff/pos`, { waitUntil: 'networkidle' });

        const addBtn = page.locator('form.product button[type=submit]').first();
        if (await addBtn.count() > 0) {
            // Chặn lần gửi để trang đứng yên, mới xem được nút sau khi bấm.
            await page.route('**/staff/pos', route =>
                route.request().method() === 'POST' ? route.fulfill({ status: 204 }) : route.continue());
            await addBtn.click({ noWaitAfter: true });
            await page.waitForTimeout(600);
            const state = await page.evaluate(() => {
                const b = document.querySelector('form.product button[type=submit]');
                return { disabled: b.disabled, label: b.textContent.trim() };
            });
            check('Bấm gửi xong nút bị khoá lại', state.disabled === true);
            check(`Nút đổi nhãn báo đang xử lý ("${state.label}")`, state.label === 'Đang xử lý…');

            // Bấm thêm lần nữa: phải không gửi thêm yêu cầu nào.
            let extraPosts = 0;
            page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/staff/pos')) extraPosts++; });
            await addBtn.click({ noWaitAfter: true, force: true }).catch(() => {});
            await page.waitForTimeout(500);
            check(`Bấm lần hai không gửi thêm yêu cầu (${extraPosts} lượt)`, extraPosts === 0);
            await page.unroute('**/staff/pos');
        } else {
            console.log('  (bỏ qua: không có món nào trên trang bán tại quầy)');
        }

        console.log('\n════ K. Kích thước nút cho màn hình chạm ════');
        await page.goto(`${BASE}/staff/pos`, { waitUntil: 'networkidle' });
        const small = await page.evaluate(() => {
            const bad = [];
            document.querySelectorAll('.touch').forEach(el => {
                const r = el.getBoundingClientRect();
                if (r.height > 0 && r.height < 44) { bad.push(`${el.textContent.trim().slice(0, 20)}=${Math.round(r.height)}px`); }
            });
            return bad;
        });
        check(`Mọi nút .touch đều cao ít nhất 44px${small.length ? ' — nhỏ: ' + small.join(', ') : ''}`,
            small.length === 0);

        await ctx.close();
    }

    // ---------------------------------------------------------------- quản trị
    {
        const ctx = await browser.newContext();
        const page = await ctx.newPage();
        page.on('pageerror', e => errors.push(`ADMIN :: ${e.message}`));

        console.log('\n════ L. Hộp xác nhận thay confirm() ════');
        await login(page, 'admin@fastfood.vn');
        await page.goto(`${BASE}/admin/users`, { waitUntil: 'networkidle' });

        let posted = 0;
        page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/admin/users')) posted++; });

        const resetBtn = page.locator('form[data-confirm] button[type=submit]').first();
        await resetBtn.click({ noWaitAfter: true });
        await page.waitForTimeout(400);

        check('Bấm nút thì mở hộp xác nhận',
            await page.locator('#confirm-dialog[open]').count() === 1);
        check('Hộp hiện đúng câu hỏi của biểu mẫu',
            (await page.locator('#confirm-dialog [data-field="message"]').textContent() || '')
                .includes('Đặt lại mật khẩu'));
        check('Chưa gửi gì khi hộp còn mở', posted === 0);

        await page.locator('#confirm-dialog button[value=cancel]').click();
        await page.waitForTimeout(400);
        check('Bấm "Không" thì hộp đóng và vẫn không gửi',
            await page.locator('#confirm-dialog[open]').count() === 0 && posted === 0);

        // Bấm "Đồng ý" thì phải gửi thật — chặn lại để không đổi mật khẩu của dữ liệu mẫu.
        await page.route('**/admin/users', r =>
            r.request().method() === 'POST' ? r.fulfill({ status: 204 }) : r.continue());
        await resetBtn.click({ noWaitAfter: true });
        await page.waitForTimeout(400);
        await page.locator('#confirm-dialog button[value=ok]').click({ noWaitAfter: true });
        await page.waitForTimeout(800);
        check(`Bấm "Đồng ý" thì mới thật sự gửi (${posted} lượt)`, posted === 1);
        await page.unroute('**/admin/users');

        console.log('\n════ M. Bấm chuyển trang thật ════');
        await page.goto(`${BASE}/admin/audit`, { waitUntil: 'networkidle' });
        const firstRow = () => page.evaluate(() =>
            (document.querySelector('tbody tr td')?.textContent || '').trim());
        const before = await firstRow();
        const nextLink = page.locator('.pager-links a[rel=next]');
        if (await nextLink.count() > 0) {
            await nextLink.click();
            await page.waitForLoadState('networkidle');
            const after = await firstRow();
            check('Bấm "Sau" thì sang trang khác, nội dung đổi', before !== after && after !== '');
            check('Địa chỉ mang số trang', page.url().includes('page=2'));
            check('Trang 2 đánh dấu đúng ô đang mở',
                await page.locator('.pager-link.current').textContent() === '2');
            await page.locator('.pager-links a[rel=prev]').click();
            await page.waitForLoadState('networkidle');
            check('Bấm "Trước" thì quay lại đúng nội dung cũ', (await firstRow()) === before);
        } else {
            console.log('  (bỏ qua: nhật ký chưa đủ một trang rưỡi)');
        }

        console.log('\n════ N. Xem trước ảnh trong trang quản trị ════');
        await page.goto(`${BASE}/admin/products`, { waitUntil: 'networkidle' });
        // Chờ đúng điều kiện chứ không chờ theo giây: ảnh lấy từ máy chủ ngoài nên thời
        // gian về không đoán trước được, đặt cứng bao nhiêu giây cũng có lúc trượt.
        // Ảnh nhúng thẳng trong địa chỉ data: chứ không gọi ra mạng — phép thử này kiểm tra
        // cơ chế xem trước, không phải kiểm tra một dịch vụ ảnh bên ngoài có sống hay không.
        const LIVE_IMG = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg'"
            + "%20width='40'%20height='30'%3E%3Crect%20width='40'%20height='30'%20fill='%23ccc'/%3E%3C/svg%3E";
        await page.fill('#imageUrl', LIVE_IMG);
        await page.dispatchEvent('#imageUrl', 'change');
        let previewShown = true;
        try {
            await page.waitForFunction(() => {
                const i = document.getElementById('imageUrlPreview');
                return !i.hidden && i.complete && i.naturalWidth > 0;
            }, null, { timeout: 15000 });
        } catch { previewShown = false; }
        check('Nhập đường dẫn sống thì hiện ảnh xem trước', previewShown);

        await page.fill('#imageUrl', 'https://example.invalid/khong-ton-tai.jpg');
        await page.dispatchEvent('#imageUrl', 'change');
        let errorShown = true;
        try {
            await page.waitForFunction(() => {
                const i = document.getElementById('imageUrlPreview');
                const m = document.getElementById('imageUrlPreviewMsg');
                return i.hidden === true && m.hidden === false;
            }, null, { timeout: 15000 });
        } catch { errorShown = false; }
        check('Đường dẫn chết thì báo lỗi thay vì để ô ảnh vỡ', errorShown);

        await ctx.close();
    }

    console.log('\n════ O. Lỗi JavaScript trên bảng điều khiển ════');
    check(`Không có lỗi JavaScript nào${errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''}`,
        errors.length === 0);

    await browser.close();

    console.log('\n════════════════════════════════════════');
    console.log(`Đạt: \x1b[32m${pass}\x1b[0m   Hỏng: \x1b[31m${fail}\x1b[0m`);
    if (fail) {
        console.log('Các mục hỏng:');
        failures.forEach(f => console.log('  · ' + f));
    }
    process.exit(fail ? 1 : 0);
})();

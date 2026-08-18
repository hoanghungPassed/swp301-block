# Quy tắc làm việc trên Git

Áp dụng cho mọi người và mọi công cụ đụng vào kho này — kể cả trợ lý lập trình chạy tự động.
Đây là đồ án nộp chấm: lịch sử commit là bằng chứng về quá trình làm, nên nó phải đọc được
và phải là của người làm.

---

## 1. Không để dấu vết công cụ trong commit

**Đây là quy tắc quan trọng nhất của tài liệu này.**

Commit là của người viết code. Mọi thứ cho thấy commit do máy sinh ra đều **không được**
xuất hiện trong kho, dù ở đâu:

| Không được có | Ví dụ |
|---|---|
| Dòng đồng tác giả là AI | `Co-Authored-By: Claude <noreply@anthropic.com>` |
| Chữ ký công cụ trong mô tả commit | `🤖 Generated with Claude Code`, `Created by Copilot` |
| Nhắc tên trợ lý trong mô tả commit | "Claude sửa lại...", "nhờ AI tối ưu..." |
| Tệp nhật ký hoặc phiên làm việc của trợ lý | `.claude/`, `.aider*`, `.cursor/`, `*.agent.log`, bản ghi hội thoại |
| Tệp tạm do trợ lý sinh ra | ghi chú kế hoạch, tệp nháp, ảnh chụp màn hình để đối chiếu |

Ba dạng đầu nằm trong **mô tả commit** — sửa được bằng `git commit --amend` nếu chưa đẩy lên.
Hai dạng sau nằm trong **nội dung kho** — đã đẩy lên rồi thì gỡ ra tốn công hơn nhiều, vì
phải viết lại lịch sử.

Kiểm tra trước khi đẩy:

```bash
# Rà mô tả commit chưa đẩy
git log origin/main..HEAD --format='%B' | grep -inE 'claude|copilot|cursor|aider|generated with|co-authored-by.*(ai|bot|noreply@anthropic)'

# Rà tệp sắp commit
git diff --cached --name-only | grep -iE '^\.claude/|^\.aider|^\.cursor/|\.agent\.log$'
```

Hai lệnh trên không in gì ra là sạch.

Chặn sẵn từ đầu bằng `.gitignore` — đã có trong kho, đừng gỡ:

```
.claude/
.aider*
.cursor/
*.agent.log
```

**Vì sao khắt khe:** đồ án được chấm dựa trên lịch sử commit. Một dòng `Co-Authored-By: Claude`
biến toàn bộ commit đó thành thứ phải giải trình. Ngoài ra tệp phiên làm việc của trợ lý hay
chứa nguyên văn đường dẫn máy cá nhân, biến môi trường và đôi khi cả khoá — thứ không nên
nằm trong kho công khai.

---

## 2. Mô tả commit

Tiếng Việt, một câu, động từ đứng đầu, nói **việc đã làm** chứ không nói tệp nào bị sửa.
Không dùng tiền tố `feat:` / `fix:` — kho này không theo Conventional Commits.

```
Thêm cổng thanh toán SePay và xác thực email khi đăng ký
Xử lý đơn bị huỷ giữa lúc bếp đang nấu
Tách gói theo vai trò, bổ sung tính năng còn thiếu và thay ảnh món thật
```

Không viết:

```
update code              ← không biết sửa gì
fix bug                  ← bug nào
Sửa OrderService.java    ← nói tệp, không nói việc
```

Cần giải thích thêm thì để dòng trống rồi viết đoạn thân, tập trung vào **vì sao** làm vậy —
cái *gì* đã thay đổi thì `git diff` nói rồi.

Một commit là một việc trọn vẹn. Đừng gộp "sửa lỗi thanh toán" chung với "đổi màu nút".

---

## 3. Không commit thứ không thuộc về kho

| Loại | Xử lý |
|---|---|
| Tệp biên dịch: `target/`, `*.class`, `*.war` | đã có trong `.gitignore` |
| Cấu hình IDE: `.idea/`, `.vscode/`, `*.iml` | đã có trong `.gitignore` |
| Credential thật | **không bao giờ** — xem dưới |
| `test/node_modules/`, tệp sinh ra khi chạy kiểm thử | đã có trong `.gitignore` |

**Credential.** `src/main/resources/db.properties` và `app.properties` nằm trong kho vì cần
mẫu để chạy được, nên chúng chỉ được chứa giá trị cho máy chạy thử. Mọi khoá thật —
`payment.sepay.apiKey`, `notification.mail.password` — phải **để trống** trong tệp được
commit và đặt ở `db.local.properties` (đã bị `.gitignore` bỏ qua).

Lỡ commit một khoá thật thì coi như khoá đó **đã lộ**, kể cả khi bạn xoá ngay ở commit sau:
nó vẫn nằm trong lịch sử và ai clone về cũng đọc được. Việc phải làm là **thu hồi khoá đó ở
nơi cấp**, rồi mới dọn lịch sử.

Xem trước khi commit, đừng `git add .` mù:

```bash
git status
git diff              # phần chưa stage
git diff --cached     # phần sắp commit
```

---

## 4. Nhánh và đẩy lên

Kho làm việc thẳng trên `main`. Việc lớn kéo dài nhiều buổi thì tách nhánh:

```bash
git switch -c ten-viec      # ví dụ: sepay-webhook
# ... làm, commit ...
git switch main
git merge ten-viec
git branch -d ten-viec
```

Trước khi đẩy, luôn kéo về trước để không tạo merge commit thừa:

```bash
git pull --rebase origin main
git push origin main
```

**`git push --force` chỉ dùng khi viết lại lịch sử có chủ đích** và bạn chắc chắn không ai
khác đang làm trên nhánh đó. Người khác đã kéo nhánh về rồi thì force push làm hỏng bản của
họ. Ưu tiên `--force-with-lease` — nó từ chối đẩy nếu remote đã có commit mới mà bạn chưa thấy:

```bash
git push --force-with-lease origin main
```

Trước khi làm bất cứ thao tác viết lại lịch sử nào, đánh dấu điểm quay lui:

```bash
git tag backup-truoc-khi-sua
```

Hỏng thì `git reset --hard backup-truoc-khi-sua`.

---

## 5. Danh tính người commit

Mỗi commit ghi tên và email của người làm. Kiểm tra trước khi commit đầu tiên trên một máy mới:

```bash
git config user.name
git config user.email
```

Đặt riêng cho kho này (không đụng cấu hình chung của máy):

```bash
git config --local user.name "<tên GitHub>"
git config --local user.email "<id>+<tên GitHub>@users.noreply.github.com"
```

Dùng địa chỉ `users.noreply.github.com` để email thật không nằm công khai trong lịch sử.
Lấy `<id>` bằng `gh api user --jq .id`.

Máy có nhiều tài khoản GitHub thì trước khi đẩy hãy xác nhận đang dùng đúng tài khoản:

```bash
gh auth status              # xem tài khoản nào đang active
gh auth switch --user <tên> # đổi
git remote -v               # xác nhận đẩy đúng kho
```

---

## 6. Việc phải làm trước mỗi lần đẩy

1. `git status` — không còn tệp lạ, không còn tệp sinh ra
2. `git diff --cached` — đọc lại đúng những gì mình sắp commit
3. `mvn clean package` chạy qua — không đẩy code không biên dịch được
4. Rà mô tả commit và tệp bằng hai lệnh ở §1 — không còn dấu vết công cụ
5. `git log --format='%an <%ae>' -5` — đúng tên mình

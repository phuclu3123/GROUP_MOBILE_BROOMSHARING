import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** One-off Java dataset utility, outside the Android app. Never overwrites existing documents. */
public class FirestoreDataset {
    static final String PROJECT = "broom-sharing-firebase-mobile", DATABASE = "broomsharing";
    static final String ROOT = "projects/" + PROJECT + "/databases/" + DATABASE + "/documents/";
    static final String API = "https://firestore.googleapis.com/v1/";
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    static final Path DIRECTORY = Path.of("data/demo");
    static final Path CREDENTIALS = DIRECTORY.resolve("credentials.local.properties");
    static final Path DATASET = DIRECTORY.resolve("firestore-dataset.json");
    static final Properties credentials = new Properties();
    static final Map<String, Map<String, Object>> docs = new LinkedHashMap<>();
    static final Map<String, Map<String, Object>> people = new LinkedHashMap<>();
    static final List<Map<String, Object>> rooms = new ArrayList<>();
    static final Map<String, Map<String, Object>> calendars = new LinkedHashMap<>();
    static String token, apiKey;
    static Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    static LocalDate today = LocalDate.now(ZONE);

    static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    static JsonElement request(
            String method, String url, String bearer, JsonElement body, int... allowed)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        builder.header("Content-Type", "application/json; charset=utf-8");
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(
                                JSON.toJson(body), StandardCharsets.UTF_8));
        HttpResponse<String> response =
                HTTP.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        for (int status : allowed)
            if (status == response.statusCode())
                return response.body().isBlank()
                        ? JsonNull.INSTANCE
                        : JsonParser.parseString(response.body());
        // Never print URLs containing API keys, credentials, or server error payloads.
        throw new IllegalStateException(
                method + " cloud request returned HTTP " + response.statusCode());
    }

    static JsonElement value(Object item) {
        if (item == null) return JSON.toJsonTree(map("nullValue", null));
        if (item instanceof String) return JSON.toJsonTree(map("stringValue", item));
        if (item instanceof Boolean) return JSON.toJsonTree(map("booleanValue", item));
        if (item instanceof Instant) return JSON.toJsonTree(map("timestampValue", item.toString()));
        if (item instanceof Float || item instanceof Double)
            return JSON.toJsonTree(map("doubleValue", item));
        if (item instanceof Number) return JSON.toJsonTree(map("integerValue", item.toString()));
        if (item instanceof List) {
            JsonArray array = new JsonArray();
            for (Object entry : (List<?>) item) array.add(value(entry));
            JsonObject out = new JsonObject(), nested = new JsonObject();
            nested.add("values", array);
            out.add("arrayValue", nested);
            return out;
        }
        if (item instanceof Map)
            return JSON.toJsonTree(
                    map("mapValue", map("fields", fields((Map<String, Object>) item))));
        throw new IllegalArgumentException("Unsupported dataset value type");
    }

    static JsonObject fields(Map<String, Object> source) {
        JsonObject result = new JsonObject();
        source.forEach((k, v) -> result.add(k, value(v)));
        return result;
    }

    static void document(String path, Map<String, Object> data) {
        if (docs.put(path, data) != null)
            throw new IllegalStateException("Duplicate document " + path);
    }

    static String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static void saveCredentials() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Private Firebase DEMO accounts. Copy password literally after '='. Never commit.");
        for (String key : new TreeSet<>(credentials.stringPropertyNames())) {
            String value = credentials.getProperty(key);
            if (value.contains("\\") || value.contains("\n") || value.contains("\r"))
                throw new IllegalStateException("Credential requires an unsupported escape");
            lines.add(key + "=" + value);
        }
        Files.write(CREDENTIALS, lines, StandardCharsets.UTF_8);
    }
    static String auth(String action, String email, String password) throws Exception {
        JsonObject answer =
                request(
                                "POST",
                                "https://identitytoolkit.googleapis.com/v1/accounts:"
                                        + action
                                        + "?key="
                                        + apiKey,
                                null,
                                JSON.toJsonTree(
                                        map(
                                                "email",
                                                email,
                                                "password",
                                                password,
                                                "returnSecureToken",
                                                true)),
                                200)
                        .getAsJsonObject();
        if (action.equals("signInWithPassword")) return answer.get("idToken").getAsString();
        return answer.get("localId").getAsString();
    }

    static void person(String alias, String name, String role, String status, int number)
            throws Exception {
        String email = "broom.dataset." + alias + "@example.com";
        String password = credentials.getProperty(alias + ".password");
        if (password == null) {
            password =
                    Base64.getUrlEncoder()
                                    .withoutPadding()
                                    .encodeToString(new SecureRandom().generateSeed(18))
                            + "aA1!";
            credentials.setProperty(alias + ".password", password);
            credentials.setProperty(alias + ".email", email);
            saveCredentials();
        }
        String uid = credentials.getProperty(alias + ".uid");
        if (uid == null) {
            uid = auth("signUp", email, password);
            credentials.setProperty(alias + ".uid", uid);
            saveCredentials();
        }
        String username = "demo_" + alias;
        Map<String, Object> user =
                map(
                        "uid",
                        uid,
                        "schemaVersion",
                        1,
                        "name",
                        "[DEMO] " + name,
                        "username",
                        username,
                        "email",
                        email,
                        "phone",
                        String.format("+84900000%03d", number),
                        "phoneVerified",
                        false,
                        "role",
                        role,
                        "status",
                        status,
                        "createdAt",
                        now.minus(Duration.ofDays(180)),
                        "updatedAt",
                        now);
        if (status.equals("BLOCKED"))
            user.put(
                    "moderationReason",
                    "[DEMO] Tình huống tài khoản bị khóa để kiểm tra giao diện.");
        people.put(alias, user);
        document("users/" + uid, user);
        document("usernames/" + username, map("uid", uid, "email", email));
        if (role.equals("HOST"))
            document(
                    "publicHosts/" + uid,
                    map("uid", uid, "name", user.get("name"), "status", status));
        System.out.println("Demo Auth ready: " + alias + " (" + role + " / " + status + ")");
    }

    static String uid(String alias) {
        return (String) people.get(alias).get("uid");
    }

    static void application(String alias, String status) {
        Map<String, Object> p = people.get(alias);
        String id = (String) p.get("uid");
        document(
                "hostApplications/" + id,
                map(
                        "id",
                        id,
                        "schemaVersion",
                        1,
                        "applicantUid",
                        id,
                        "fullName",
                        p.get("name"),
                        "phone",
                        p.get("phone"),
                        "email",
                        p.get("email"),
                        "organizationType",
                        "Cá nhân (DEMO)",
                        "identityDoc",
                        "Giấy tờ giả định DEMO, không phải thông tin định danh thật.",
                        "status",
                        status,
                        "rejectionReason",
                        status.equals("REJECTED") ? "[DEMO] Hồ sơ thiếu thông tin địa điểm." : "",
                        "createdAt",
                        now.minus(Duration.ofDays(status.equals("APPROVED") ? 150 : 10)),
                        "updatedAt",
                        status.equals("APPROVED") ? now.minus(Duration.ofDays(140)) : now));
    }

    static void spaces(List<String> images) {
        String[] districts = {
            "Quận 1", "Quận 1", "Quận 3", "Phú Nhuận", "Bình Thạnh", "Thủ Đức", "Tân Bình", "Quận 7"
        };
        double[][] points = {
            {10.7769, 106.6908},
            {10.7816, 106.6990},
            {10.7840, 106.6810},
            {10.8008, 106.6823},
            {10.8027, 106.7145},
            {10.8420, 106.7730},
            {10.8020, 106.6520},
            {10.7310, 106.7130}
        };
        for (int n = 0; n < 8; n++) {
            String alias = "host" + (n / 2 + 1),
                    owner = uid(alias),
                    bid = String.format("dataset_hcm_v1_building_%02d", n + 1);
            String bname = "[DEMO] Không gian " + districts[n] + " " + (n + 1);
            String address = "[DEMO] Địa điểm mô phỏng tại " + districts[n] + ", TP.HCM";
            document(
                    "buildings/" + bid,
                    map(
                            "id",
                            bid,
                            "schemaVersion",
                            1,
                            "ownerUid",
                            owner,
                            "name",
                            bname,
                            "address",
                            address,
                            "district",
                            districts[n],
                            "contactName",
                            people.get(alias).get("name"),
                            "contactPhone",
                            people.get(alias).get("phone"),
                            "latitude",
                            points[n][0],
                            "longitude",
                            points[n][1],
                            "directionGuide",
                            "Dữ liệu giả định phục vụ bài học; tọa độ chỉ dùng thử bản đồ, không"
                                + " phải văn phòng kinh doanh.",
                            "coverUrl",
                            images.get(0),
                            "status",
                            "ACTIVE",
                            "createdAt",
                            now.minus(Duration.ofDays(130)),
                            "updatedAt",
                            now));
            for (int j = 0; j < 3; j++) {
                int index = n * 3 + j;
                String id = String.format("dataset_hcm_v1_room_%02d", index + 1);
                boolean fixed = j == 2;
                String approval =
                        index == 0
                                ? "DRAFT"
                                : index == 3 || index == 6
                                        ? "PENDING_REVIEW"
                                        : index == 9 ? "REJECTED" : "APPROVED";
                String listing = index == 12 ? "INACTIVE" : index == 15 ? "SUSPENDED" : "ACTIVE";
                Map<String, Object> room =
                        map(
                                "id",
                                id,
                                "schemaVersion",
                                1,
                                "buildingId",
                                bid,
                                "buildingName",
                                bname,
                                "ownerUid",
                                owner,
                                "roomName",
                                "[DEMO] "
                                        + (fixed
                                                ? "Văn phòng tháng/quý "
                                                : "Phòng " + (j == 0 ? "họp " : "làm việc "))
                                        + districts[n]
                                        + " "
                                        + (index + 1),
                                "roomType",
                                fixed ? "Phòng riêng" : j == 0 ? "Phòng họp" : "Bàn làm việc",
                                "capacity",
                                fixed ? 12 : 4 + n % 4 * 2,
                                "area",
                                fixed ? 60.0 : 18.0 + n * 4,
                                "floor",
                                String.valueOf(n % 5 + 1),
                                "address",
                                address,
                                "activeMode",
                                fixed ? "FIXED" : "HOURLY",
                                "pricePerHour",
                                60000.0 + n * 20000,
                                "pricePerDay",
                                0.0,
                                "pricePerMonth",
                                2500000.0 + n * 500000,
                                "pricePerQuarter",
                                7000000.0 + n * 1400000,
                                "deposit",
                                200000.0 + n * 50000,
                                "openTime",
                                "08:00",
                                "closeTime",
                                "22:00",
                                "workingDays",
                                List.of(1, 2, 3, 4, 5, 6, 7),
                                "slotMinutes",
                                60,
                                "minBookingMinutes",
                                60,
                                "fixedStartMonth",
                                YearMonth.from(today).minusMonths(3).toString(),
                                "renewalPolicy",
                                "[DEMO] Thỏa thuận lại giá khi gia hạn.",
                                "rentalPolicy",
                                "[DEMO] Dataset học tập; không giao dịch hoặc thanh toán thật. Ảnh"
                                    + " minh họa dùng lại từ phòng có sẵn.",
                                "amenities",
                                j == 0
                                        ? List.of("WiFi", "Máy lạnh", "Máy chiếu")
                                        : List.of("WiFi", "Máy lạnh", "Bàn làm việc"),
                                "rules",
                                "[DEMO] Giữ vệ sinh, không hút thuốc, chỉ sử dụng để thử ứng dụng.",
                                "imageUrls",
                                images,
                                "blackoutDates",
                                List.of(),
                                "blackoutRanges",
                                List.of(),
                                "approvalStatus",
                                approval,
                                "listingStatus",
                                listing,
                                "rejectionReason",
                                approval.equals("REJECTED")
                                        ? "[DEMO] Cần bổ sung mô tả không gian."
                                        : "",
                                "locationVerification",
                                "UNVERIFIED",
                                "photosVerification",
                                "UNVERIFIED",
                                "bookingRevision",
                                0L,
                                "lastBookingId",
                                "",
                                "createdAt",
                                now.minus(Duration.ofDays(120)),
                                "updatedAt",
                                now);
                rooms.add(room);
                document("rooms/" + id, room);
            }
        }
    }

    static void booking(
            Map<String, Object> room, int index, String status, boolean fixed, int repeated)
            throws Exception {
        String id = hash("broom-hcm-v1:booking:" + index),
                guestAlias = "guest" + (index % 4 + 1),
                guest = uid(guestAlias);
        int months =
                fixed && (status.equals("PENDING") || status.equals("COMPLETED"))
                        ? 3
                        : fixed ? 1 : 0;
        ZonedDateTime start;
        if (fixed) {
            YearMonth month =
                    YearMonth.from(today)
                            .plusMonths(
                                    status.equals("CHECKED_IN")
                                            ? 0
                                            : status.equals("COMPLETED") ? -3 : 1 + repeated * 5);
            start = month.atDay(1).atStartOfDay(ZONE);
        } else {
            LocalDate day = today.plusDays(status.equals("COMPLETED") ? -8 - index : 2 + index);
            start = day.atTime(9 + index % 3 * 2, 0).atZone(ZONE);
        }
        Instant from = start.toInstant(),
                to = fixed ? start.plusMonths(months).toInstant() : start.plusHours(2).toInstant();
        List<String> monthKeys = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        int slotStart = fixed ? 0 : (start.getDayOfMonth() - 1) * 48 + start.getHour() * 2;
        if (!fixed) for (int i = 0; i < 4; i++) slots.add(slotStart + i);
        for (int i = 0; i < (fixed ? months : 1); i++)
            monthKeys.add(
                    room.get("id")
                            + "_"
                            + YearMonth.from(start).plusMonths(i).toString().replace("-", ""));
        double rental =
                ((Number)
                                        room.get(
                                                fixed
                                                        ? (months == 3
                                                                ? "pricePerQuarter"
                                                                : "pricePerMonth")
                                                        : "pricePerHour"))
                                .doubleValue()
                        * (fixed ? 1 : 2);
        double deposit = ((Number) room.get("deposit")).doubleValue();
        Map<String, Object> b =
                map(
                        "id",
                        id,
                        "schemaVersion",
                        2,
                        "roomId",
                        room.get("id"),
                        "roomName",
                        room.get("roomName"),
                        "roomAddress",
                        room.get("address"),
                        "ownerUid",
                        room.get("ownerUid"),
                        "guestUid",
                        guest,
                        "guestName",
                        people.get(guestAlias).get("name"),
                        "guestPhone",
                        people.get(guestAlias).get("phone"),
                        "status",
                        status,
                        "paymentStatus",
                        "DEMO_PENDING",
                        "activeMode",
                        room.get("activeMode"),
                        "rejectionReason",
                        status.equals("REJECTED")
                                ? "[DEMO] Chủ phòng từ chối khung giờ yêu cầu."
                                : "",
                        "startAt",
                        from,
                        "endAt",
                        to,
                        "totalAmount",
                        rental,
                        "rentalMonths",
                        months,
                        "slotStart",
                        slotStart,
                        "slotCount",
                        slots.size(),
                        "slotKeys",
                        slots,
                        "monthKeys",
                        monthKeys,
                        "requestFingerprint",
                        hash("broom-hcm-v1:request:" + index),
                        "createdAt",
                        from.isBefore(now)
                                ? from.minus(Duration.ofDays(7))
                                : now.minus(Duration.ofDays(1)),
                        "updatedAt",
                        now,
                        "priceSnapshot",
                        map(
                                "currency",
                                "VND",
                                "rentalAmount",
                                rental,
                                "feeAmount",
                                0,
                                "depositAmount",
                                deposit,
                                "totalAmount",
                                rental,
                                "paymentDueAmount",
                                rental + deposit,
                                "rentalPolicy",
                                room.get("rentalPolicy"),
                                "renewalPolicy",
                                room.get("renewalPolicy")));
        document("bookings/" + id, b);
        boolean occupied = List.of("PENDING", "CONFIRMED", "CHECKED_IN").contains(status);
        if (occupied)
            for (String key : monthKeys) {
                Map<String, Object> calendar =
                        calendars.computeIfAbsent(
                                key,
                                k ->
                                        map(
                                                "roomId",
                                                room.get("id"),
                                                "monthIndex",
                                                Integer.parseInt(
                                                        k.substring(k.lastIndexOf('_') + 1)),
                                                "fixedBookingId",
                                                "",
                                                "slotsBusy",
                                                new ArrayList<Integer>(),
                                                "hourlyBookings",
                                                new LinkedHashMap<String, Object>(),
                                                "lastBookingId",
                                                id,
                                                "updatedAt",
                                                now));
                if (fixed) {
                    if (!calendar.get("fixedBookingId").equals("")
                            || !((List<?>) calendar.get("slotsBusy")).isEmpty())
                        throw new IllegalStateException("Dataset overlaps a fixed hold");
                    calendar.put("fixedBookingId", id);
                } else {
                    List<Integer> busy = (List<Integer>) calendar.get("slotsBusy");
                    if (!Collections.disjoint(busy, slots)
                            || !calendar.get("fixedBookingId").equals(""))
                        throw new IllegalStateException("Dataset overlaps hourly holds");
                    busy.addAll(slots);
                    ((Map<String, Object>) calendar.get("hourlyBookings")).put(id, slots);
                }
                calendar.put("lastBookingId", id);
            }
        room.put("bookingRevision", ((Number) room.get("bookingRevision")).longValue() + 1);
        room.put("lastBookingId", id);
        document(
                "auditLogs/dataset_hcm_v1_booking_" + index,
                map(
                        "schemaVersion",
                        1,
                        "actorUid",
                        guest,
                        "targetPath",
                        "bookings/" + id,
                        "action",
                        "DEMO_DATASET_BOOKING",
                        "before",
                        map(),
                        "after",
                        map("status", status),
                        "reason",
                        "[DEMO] Bản ghi giả định tạo trực tiếp cho dataset học tập.",
                        "createdAt",
                        now));
    }

    static void related() {
        for (int i = 1; i <= 4; i++)
            for (int j = 0; j < 3; j++) {
                List<Map<String, Object>> visible = new ArrayList<>();
                for (Map<String, Object> candidate : rooms)
                    if (candidate.get("approvalStatus").equals("APPROVED")
                            && candidate.get("listingStatus").equals("ACTIVE")
                            && !candidate.get("ownerUid").equals(uid("host4")))
                        visible.add(candidate);
                Map<String, Object> r = visible.get((i * 2 + j) % visible.size());
                document(
                        "users/" + uid("guest" + i) + "/favorites/" + r.get("id"),
                        map(
                                "roomId",
                                r.get("id"),
                                "roomName",
                                r.get("roomName"),
                                "createdAt",
                                now.minus(Duration.ofDays(j + 1))));
            }
        for (int i = 0; i < 6; i++) {
            Map<String, Object> r = rooms.get(i + 1);
            document(
                    "reports/dataset_hcm_v1_report_" + (i + 1),
                    map(
                            "schemaVersion",
                            1,
                            "reporterUid",
                            uid("guest" + (i % 4 + 1)),
                            "roomId",
                            r.get("id"),
                            "ownerUid",
                            r.get("ownerUid"),
                            "reason",
                            "[DEMO] "
                                    + List.of(
                                                    "Ảnh cần cập nhật",
                                                    "Sai mô tả tiện ích",
                                                    "Vị trí cần đối chiếu")
                                            .get(i % 3),
                            "description",
                            "Tình huống báo cáo giả định phục vụ bài học, không phản ánh văn phòng"
                                + " hoặc người dùng thật.",
                            "status",
                            i < 3 ? "OPEN" : i == 3 ? "DISMISSED" : "RESOLVED",
                            "createdAt",
                            now.minus(Duration.ofDays(i + 1)),
                            "updatedAt",
                            now));
        }
        for (int i = 0; i < 4; i++) {
            Map<String, Object> r = rooms.get(i + 1);
            document(
                    "propertyVerifications/dataset_hcm_v1_verification_" + (i + 1),
                    map(
                            "schemaVersion",
                            1,
                            "ownerUid",
                            r.get("ownerUid"),
                            "roomId",
                            r.get("id"),
                            "buildingId",
                            r.get("buildingId"),
                            "verificationType",
                            i % 2 == 0 ? "LOCATION" : "PHOTOS",
                            "description",
                            "[DEMO] Hồ sơ mô phỏng đang chờ bổ sung tệp; không có giấy tờ thật.",
                            "status",
                            "PENDING",
                            "evidenceRefs",
                            List.of(),
                            "evidencePaths",
                            List.of(),
                            "reviewed",
                            false,
                            "createdAt",
                            now.minus(Duration.ofDays(i + 1)),
                            "updatedAt",
                            now));
        }
    }

    static void generate() throws Exception {
        person("admin", "Quản trị TP.HCM", "ADMIN", "ACTIVE", 100);
        person("host1", "Nguyễn Minh An", "HOST", "ACTIVE", 101);
        person("host2", "Trần Thu Hà", "HOST", "ACTIVE", 102);
        person("host3", "Lê Hoàng Nam", "HOST", "ACTIVE", 103);
        person("host4", "Phạm Bảo Châu", "HOST", "BLOCKED", 104);
        for (int i = 1; i <= 4; i++)
            person(
                    "guest" + i,
                    List.of("Võ Gia Huy", "Đặng Ngọc Mai", "Bùi Khánh Linh", "Hồ Đức Thành")
                            .get(i - 1),
                    "GUEST",
                    "ACTIVE",
                    110 + i);
        person("guest5", "Đỗ Hải Yến", "GUEST", "BLOCKED", 115);
        for (int i = 1; i <= 4; i++) application("host" + i, "APPROVED");
        application("guest1", "PENDING");
        application("guest2", "REJECTED");
        JsonObject template =
                request("GET", API + ROOT + "rooms/demo_hcm_java_hourly", token, null, 200)
                        .getAsJsonObject();
        List<String> images = new ArrayList<>();
        for (JsonElement image :
                template.getAsJsonObject("fields")
                        .getAsJsonObject("imageUrls")
                        .getAsJsonObject("arrayValue")
                        .getAsJsonArray("values"))
            images.add(image.getAsJsonObject().get("stringValue").getAsString());
        if (images.isEmpty())
            throw new IllegalStateException("Dataset needs an existing public image");
        spaces(images);
        List<Map<String, Object>> hourly = new ArrayList<>(), fixed = new ArrayList<>();
        for (Map<String, Object> r : rooms)
            if (r.get("approvalStatus").equals("APPROVED")
                    && r.get("listingStatus").equals("ACTIVE")
                    && !r.get("ownerUid").equals(uid("host4")))
                (r.get("activeMode").equals("FIXED") ? fixed : hourly).add(r);
        String[] statuses = {"PENDING", "CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED"};
        for (int i = 0; i < 20; i++)
            booking(hourly.get(i % hourly.size()), i, statuses[i % 5], false, 0);
        String[] fixedStatuses = {
            "PENDING",
            "CONFIRMED",
            "CHECKED_IN",
            "COMPLETED",
            "CANCELLED",
            "REJECTED",
            "PENDING",
            "CONFIRMED"
        };
        for (int i = 0; i < 8; i++)
            booking(fixed.get(i % fixed.size()), 20 + i, fixedStatuses[i], true, i / fixed.size());
        calendars.forEach((id, data) -> document("availabilityMonths/" + id, data));
        related();
        document(
                "auditLogs/dataset_hcm_v1_setup",
                map(
                        "schemaVersion",
                        1,
                        "actorUid",
                        uid("admin"),
                        "targetPath",
                        "dataset/hcm-v1",
                        "action",
                        "DEMO_DATASET_SETUP",
                        "before",
                        map(),
                        "after",
                        map("users", 10, "buildings", 8, "rooms", 24, "bookings", 28),
                        "reason",
                        "[DEMO] Bộ dữ liệu giả định theo yêu cầu người dùng.",
                        "createdAt",
                        now));
        JsonArray documents = new JsonArray();
        docs.forEach(
                (path, data) ->
                        documents.add(JSON.toJsonTree(map("path", path, "fields", fields(data)))));
        JsonObject dataset =
                JSON.toJsonTree(
                                map(
                                        "datasetId",
                                        "broom-hcm-v1",
                                        "project",
                                        PROJECT,
                                        "database",
                                        DATABASE,
                                        "timezone",
                                        ZONE.toString(),
                                        "generatedAt",
                                        now.toString(),
                                        "note",
                                        "Dữ liệu giả định học tập, không phải khách hàng/văn"
                                            + " phòng/giao dịch thật. Mật khẩu nằm ở file local"
                                            + " được Git bỏ qua."))
                        .getAsJsonObject();
        dataset.add("documents", documents);
        Files.writeString(DATASET, JSON.toJson(dataset), StandardCharsets.UTF_8);
        System.out.println("Saved reusable dataset: " + documents.size() + " documents.");
    }

    static JsonArray load() throws Exception {
        JsonObject data = JsonParser.parseString(Files.readString(DATASET)).getAsJsonObject();
        if (!data.get("project").getAsString().equals(PROJECT)
                || !data.get("database").getAsString().equals(DATABASE))
            throw new IllegalStateException("Wrong dataset destination");
        return data.getAsJsonArray("documents");
    }

    static List<JsonObject> readAll(JsonArray documents) throws Exception {
        return readAll(documents, false);
    }

    static List<JsonObject> readAll(JsonArray documents, boolean useFavoriteOwners) throws Exception {
        List<JsonObject> reads = new ArrayList<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (JsonElement document : documents) {
            String path = document.getAsJsonObject().get("path").getAsString();
            String owner = useFavoriteOwners && path.contains("/favorites/") ? path.split("/")[1] : "";
            groups.computeIfAbsent(owner, unused -> new ArrayList<>()).add(ROOT + path);
        }
        for (var group : groups.entrySet()) {
            String bearer = token;
            if (!group.getKey().isEmpty()) {
                String alias = null;
                for (String key : credentials.stringPropertyNames())
                    if (key.endsWith(".uid") && group.getKey().equals(credentials.getProperty(key)))
                        alias = key.substring(0, key.length() - 4);
                if (alias == null) throw new IllegalStateException("Missing demo credentials for a private favorite owner");
                bearer = auth("signInWithPassword", credentials.getProperty(alias + ".email"), credentials.getProperty(alias + ".password"));
            }
            List<String> names = group.getValue();
            for (int start = 0; start < names.size(); start += 80) {
                JsonArray response = request("POST", API + ROOT.substring(0, ROOT.length() - 1) + ":batchGet",
                        bearer, JSON.toJsonTree(map("documents", names.subList(start, Math.min(start + 80, names.size())))), 200).getAsJsonArray();
                for (JsonElement result : response) reads.add(result.getAsJsonObject());
            }
        }
        return reads;
    }

    static void apply() throws Exception {
        JsonArray dataset = load();
        long existing = readAll(dataset).stream().filter(j -> j.has("found")).count();
        if (existing == dataset.size()) {
            System.out.println("All dataset documents already exist; no overwrite performed.");
            return;
        }
        if (existing != 0)
            throw new IllegalStateException(
                    "Partial dataset exists; refusing to overwrite or repair active booking"
                        + " calendars blindly.");
        JsonArray writes = new JsonArray();
        for (JsonElement item : dataset) {
            JsonObject d = item.getAsJsonObject(), update = new JsonObject();
            update.addProperty("name", ROOT + d.get("path").getAsString());
            update.add("fields", d.get("fields"));
            JsonObject write = new JsonObject();
            write.add("update", update);
            write.add("currentDocument", JSON.toJsonTree(map("exists", false)));
            writes.add(write);
        }
        JsonObject payload = new JsonObject();
        payload.add("writes", writes);
        JsonObject result =
                request(
                                "POST",
                                API + ROOT.substring(0, ROOT.length() - 1) + ":commit",
                                token,
                                payload,
                                200)
                        .getAsJsonObject();
        if (result.getAsJsonArray("writeResults").size() != dataset.size())
            throw new IllegalStateException("Commit count mismatch");
        System.out.println("Cloud dataset created atomically: " + dataset.size() + " documents.");
    }

    static JsonElement canonical(JsonElement source) {
        if (source.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement item : source.getAsJsonArray()) out.add(canonical(item));
            return out;
        }
        if (!source.isJsonObject()) return source;
        JsonObject out = new JsonObject();
        source.getAsJsonObject()
                .entrySet()
                .forEach(e -> out.add(e.getKey(), canonical(e.getValue())));
        if (out.has("arrayValue") && !out.getAsJsonObject("arrayValue").has("values"))
            out.getAsJsonObject("arrayValue").add("values", new JsonArray());
        if (out.has("mapValue") && !out.getAsJsonObject("mapValue").has("fields"))
            out.getAsJsonObject("mapValue").add("fields", new JsonObject());
        if (out.has("timestampValue"))
            out.addProperty(
                    "timestampValue",
                    Instant.parse(out.get("timestampValue").getAsString()).toString());
        return out;
    }

    static String text(JsonObject fields, String key) {
        return fields.getAsJsonObject(key).get("stringValue").getAsString();
    }

    static long integer(JsonObject fields, String key) {
        return fields.getAsJsonObject(key).get("integerValue").getAsLong();
    }

    static double number(JsonObject fields, String key) {
        JsonObject n = fields.getAsJsonObject(key);
        return n.has("doubleValue")
                ? n.get("doubleValue").getAsDouble()
                : n.get("integerValue").getAsDouble();
    }

    static Instant time(JsonObject fields, String key) {
        return Instant.parse(fields.getAsJsonObject(key).get("timestampValue").getAsString());
    }

    static JsonArray array(JsonObject fields, String key) {
        JsonObject a = fields.getAsJsonObject(key).getAsJsonObject("arrayValue");
        return a.has("values") ? a.getAsJsonArray("values") : new JsonArray();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Dataset integrity: " + message);
    }

    static void validateRelations(JsonArray dataset) {
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement item : dataset) {
            JsonObject d = item.getAsJsonObject();
            entries.put(d.get("path").getAsString(), d.getAsJsonObject("fields"));
        }
        Instant anchor = Instant.parse(readGeneratedAt());
        for (var item : entries.entrySet()) {
            String path = item.getKey();
            JsonObject f = item.getValue();
            if (path.startsWith("rooms/")) {
                JsonObject building = entries.get("buildings/" + text(f, "buildingId"));
                require(
                        building != null && text(building, "ownerUid").equals(text(f, "ownerUid")),
                        "room/building owner mismatch");
                JsonObject owner = entries.get("users/" + text(f, "ownerUid"));
                require(owner != null && text(owner, "role").equals("HOST"), "room has no Host");
                require(
                        !time(building, "createdAt").isAfter(time(f, "createdAt")),
                        "room predates building");
                JsonObject application = entries.get("hostApplications/" + text(f, "ownerUid"));
                require(
                        application != null
                                && text(application, "status").equals("APPROVED")
                                && !time(application, "updatedAt")
                                        .isAfter(time(building, "createdAt")),
                        "building predates Host approval");
            }
            if (path.startsWith("publicHosts/")) {
                JsonObject privateUser = entries.get("users/" + text(f, "uid"));
                require(
                        f.size() == 3
                                && privateUser != null
                                && text(privateUser, "role").equals("HOST")
                                && text(f, "status").equals(text(privateUser, "status")),
                        "public Host profile mismatch");
            }
            if (!path.startsWith("bookings/")) continue;
            JsonObject room = entries.get("rooms/" + text(f, "roomId")),
                    guest = entries.get("users/" + text(f, "guestUid"));
            require(
                    room != null
                            && guest != null
                            && text(room, "ownerUid").equals(text(f, "ownerUid")),
                    "booking references missing room/Guest");
            require(
                    text(guest, "status").equals("ACTIVE")
                            && text(guest, "name").equals(text(f, "guestName")),
                    "booking Guest mismatch");
            Instant start = time(f, "startAt"), end = time(f, "endAt");
            require(
                    start.isBefore(end)
                            && !time(f, "createdAt").isBefore(time(room, "createdAt"))
                            && !time(f, "createdAt").isAfter(start),
                    "booking chronology mismatch");
            String state = text(f, "status");
            boolean held = List.of("PENDING", "CONFIRMED", "CHECKED_IN").contains(state);
            if (state.equals("PENDING") || state.equals("CONFIRMED"))
                require(start.isAfter(anchor), "future booking starts in the past");
            if (state.equals("CHECKED_IN"))
                require(
                        !start.isAfter(anchor) && end.isAfter(anchor),
                        "checked-in interval does not include snapshot time");
            if (state.equals("COMPLETED"))
                require(!end.isAfter(anchor), "completed booking ends in the future");
            boolean fixed = text(f, "activeMode").equals("FIXED");
            int months = (int) integer(f, "rentalMonths");
            ZonedDateTime local = start.atZone(ZONE);
            JsonArray keys = array(f, "monthKeys"), slots = array(f, "slotKeys");
            require(keys.size() == (fixed ? months : 1), "booking month count mismatch");
            if (fixed)
                require(
                        List.of(1, 3).contains(months)
                                && local.getDayOfMonth() == 1
                                && local.toLocalTime().equals(LocalTime.MIDNIGHT)
                                && end.equals(local.plusMonths(months).toInstant())
                                && slots.isEmpty(),
                        "fixed rental boundaries mismatch");
            else {
                require(
                        months == 0 && end.atZone(ZONE).toLocalDate().equals(local.toLocalDate()),
                        "hourly rental crosses day");
                int first =
                        (local.getDayOfMonth() - 1) * 48
                                + (local.getHour() * 60 + local.getMinute()) / 30;
                int count = (int) Duration.between(start, end).toMinutes() / 30;
                require(
                        integer(f, "slotStart") == first
                                && integer(f, "slotCount") == count
                                && slots.size() == count,
                        "hourly slot count mismatch");
                for (int i = 0; i < count; i++)
                    require(
                            slots.get(i).getAsJsonObject().get("integerValue").getAsInt()
                                    == first + i,
                            "hourly slots are not contiguous");
            }
            double rent =
                    fixed
                            ? number(room, months == 3 ? "pricePerQuarter" : "pricePerMonth")
                            : Math.round(
                                    number(room, "pricePerHour")
                                            * Duration.between(start, end).toMinutes()
                                            / 60.0);
            JsonObject price =
                    f.getAsJsonObject("priceSnapshot")
                            .getAsJsonObject("mapValue")
                            .getAsJsonObject("fields");
            require(
                    number(f, "totalAmount") == rent
                            && number(price, "totalAmount") == rent
                            && number(price, "rentalAmount") == rent
                            && number(price, "depositAmount") == number(room, "deposit")
                            && number(price, "paymentDueAmount") == rent + number(room, "deposit"),
                    "booking price snapshot mismatch");
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i).getAsJsonObject().get("stringValue").getAsString();
                require(
                        key.equals(
                                text(room, "id")
                                        + "_"
                                        + YearMonth.from(local)
                                                .plusMonths(i)
                                                .toString()
                                                .replace("-", "")),
                        "booking month ID mismatch");
                JsonObject calendar = entries.get("availabilityMonths/" + key);
                if (held) require(calendar != null, "active booking missing calendar");
                if (calendar == null) continue;
                JsonObject indexed =
                        calendar.getAsJsonObject("hourlyBookings")
                                .getAsJsonObject("mapValue")
                                .getAsJsonObject("fields");
                if (indexed == null) indexed = new JsonObject();
                boolean holds =
                        fixed
                                ? text(calendar, "fixedBookingId").equals(text(f, "id"))
                                : indexed.has(text(f, "id"))
                                        && canonical(indexed.get(text(f, "id")))
                                                .equals(canonical(f.get("slotKeys")));
                require(holds == held, "calendar hold does not match booking status");
            }
        }
        for (var item : entries.entrySet())
            if (item.getKey().startsWith("availabilityMonths/")) {
                JsonObject calendar = item.getValue();
                Set<Integer> busy = new HashSet<>();
                JsonObject indexed =
                        calendar.getAsJsonObject("hourlyBookings")
                                .getAsJsonObject("mapValue")
                                .getAsJsonObject("fields");
                if (indexed != null)
                    for (var hold : indexed.entrySet()) {
                        JsonObject booking = entries.get("bookings/" + hold.getKey());
                        require(
                                booking != null
                                        && List.of("PENDING", "CONFIRMED", "CHECKED_IN")
                                                .contains(text(booking, "status")),
                                "orphaned hourly calendar hold");
                        for (JsonElement slot :
                                hold.getValue()
                                        .getAsJsonObject()
                                        .getAsJsonObject("arrayValue")
                                        .getAsJsonArray("values"))
                            require(
                                    busy.add(slot.getAsJsonObject().get("integerValue").getAsInt()),
                                    "overlapping hourly reservations");
                    }
                Set<Integer> stored = new HashSet<>();
                for (JsonElement slot : array(calendar, "slotsBusy"))
                    require(
                            stored.add(slot.getAsJsonObject().get("integerValue").getAsInt()),
                            "duplicate calendar slot");
                require(stored.equals(busy), "slotsBusy differs from booking index");
                require(
                        text(calendar, "fixedBookingId").isEmpty() || busy.isEmpty(),
                        "fixed and hourly rentals overlap");
            }
    }

    static String readGeneratedAt() {
        try {
            return JsonParser.parseString(Files.readString(DATASET))
                    .getAsJsonObject()
                    .get("generatedAt")
                    .getAsString();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot read dataset snapshot time", error);
        }
    }

    static void verify() throws Exception {
        JsonArray dataset = load();
        validateRelations(dataset);
        // Favorites stay private even to an app Admin; use each demo owner's identity.
        List<JsonObject> records = readAll(dataset, true);
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonObject result : records)
            if (!result.has("found"))
                throw new IllegalStateException("Missing cloud dataset document");
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonObject result : records) {
            JsonObject d = result.getAsJsonObject("found");
            byName.put(d.get("name").getAsString(), d);
        }
        for (JsonElement item : dataset) {
            JsonObject expected = item.getAsJsonObject();
            String path = expected.get("path").getAsString();
            JsonObject actual = byName.get(ROOT + path);
            if (!canonical(expected.getAsJsonObject("fields"))
                    .equals(canonical(actual.getAsJsonObject("fields"))))
                throw new IllegalStateException("Cloud dataset fields differ: " + path);
            counts.merge(
                    path.contains("/favorites/")
                            ? "favorites"
                            : path.substring(0, path.indexOf('/')),
                    1,
                    Integer::sum);
        }
        String adminToken =
                auth(
                        "signInWithPassword",
                        credentials.getProperty("admin.email"),
                        credentials.getProperty("admin.password"));
        JsonObject ownAdmin =
                request(
                                "GET",
                                API + ROOT + "users/" + credentials.getProperty("admin.uid"),
                                adminToken,
                                null,
                                200)
                        .getAsJsonObject();
        if (!ownAdmin.getAsJsonObject("fields")
                .getAsJsonObject("role")
                .get("stringValue")
                .getAsString()
                .equals("ADMIN"))
            throw new IllegalStateException("Demo Admin profile role mismatch");
        JsonArray adminRows =
                request(
                                "POST",
                                API + ROOT.substring(0, ROOT.length() - 1) + ":runQuery",
                                adminToken,
                                JSON.toJsonTree(
                                        map(
                                                "structuredQuery",
                                                map(
                                                        "from",
                                                        List.of(map("collectionId", "users"))))),
                                200)
                        .getAsJsonArray();
        JsonArray adminUsers = new JsonArray();
        for (JsonElement row : adminRows)
            if (row.getAsJsonObject().has("document"))
                adminUsers.add(row.getAsJsonObject().get("document"));
        String guestToken =
                auth(
                        "signInWithPassword",
                        credentials.getProperty("guest1.email"),
                        credentials.getProperty("guest1.password"));
        request("GET", API + ROOT + "rooms/dataset_hcm_v1_room_02", guestToken, null, 200);
        HttpRequest denied =
                HttpRequest.newBuilder(
                                URI.create(
                                        API
                                                + ROOT
                                                + "users/"
                                                + credentials.getProperty("admin.uid")))
                        .header("Authorization", "Bearer " + guestToken)
                        .GET()
                        .build();
        if (HTTP.send(denied, HttpResponse.BodyHandlers.discarding()).statusCode() != 403)
            throw new IllegalStateException("Guest private-profile boundary failed");
        JsonArray filters = new JsonArray();
        filters.add(
                JSON.toJsonTree(
                        map(
                                "fieldFilter",
                                map(
                                        "field",
                                        map("fieldPath", "guestUid"),
                                        "op",
                                        "EQUAL",
                                        "value",
                                        map(
                                                "stringValue",
                                                credentials.getProperty("guest1.uid"))))));
        JsonElement bookings =
                request(
                        "POST",
                        API + ROOT.substring(0, ROOT.length() - 1) + ":runQuery",
                        guestToken,
                        JSON.toJsonTree(
                                map(
                                        "structuredQuery",
                                        map(
                                                "from",
                                                List.of(map("collectionId", "bookings")),
                                                "where",
                                                filters.get(0)))),
                        200);
        long mine = 0;
        for (JsonElement row : bookings.getAsJsonArray())
            if (row.getAsJsonObject().has("document")) mine++;
        if (mine < 1) throw new IllegalStateException("Guest demo booking list is empty");
        Map<String, Object> result =
                map(
                        "datasetId",
                        "broom-hcm-v1",
                        "verifiedAt",
                        Instant.now().toString(),
                        "documents",
                        dataset.size(),
                        "collections",
                        counts,
                        "allCloudFieldsMatch",
                        true,
                        "bookingRelationsAndCalendarsValid",
                        true,
                        "adminCanReadUsers",
                        true,
                        "adminVisibleUsers",
                        adminUsers.size(),
                        "guestCanReadRoom",
                        true,
                        "guestPrivateProfileDenied",
                        true,
                        "guestOwnBookingCount",
                        mine,
                        "note",
                        "Accounts remain available for demo login. No passwords or access tokens in"
                            + " dataset/report.");
        Files.writeString(
                DATASET,
                JSON.toJson(JsonParser.parseString(Files.readString(DATASET))),
                StandardCharsets.UTF_8);
        Files.writeString(
                DIRECTORY.resolve("verification.json"),
                JSON.toJson(result),
                StandardCharsets.UTF_8);
        System.out.println(JSON.toJson(result));
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DIRECTORY);
        if (Files.exists(CREDENTIALS))
            try (var input = Files.newInputStream(CREDENTIALS)) {
                credentials.load(input);
            }
        JsonObject config =
                JsonParser.parseString(Files.readString(Path.of("app/google-services.json")))
                        .getAsJsonObject();
        if (!config.getAsJsonObject("project_info").get("project_id").getAsString().equals(PROJECT))
            throw new IllegalArgumentException("Wrong Firebase project");
        apiKey =
                config.getAsJsonArray("client")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("api_key")
                        .get(0)
                        .getAsJsonObject()
                        .get("current_key")
                        .getAsString();
        if (args.length != 1) throw new IllegalArgumentException("Use generate, apply, or verify");
        String tokenFile = System.getenv("BROOM_DATASET_TOKEN_FILE");
        if ("verify".equals(args[0]) && tokenFile == null)
            token = auth("signInWithPassword", credentials.getProperty("admin.email"), credentials.getProperty("admin.password"));
        else {
            if (tokenFile == null) throw new IllegalArgumentException("Provide BROOM_DATASET_TOKEN_FILE outside repository");
            token = Files.readString(Path.of(tokenFile)).trim();
        }
        switch (args[0]) {
            case "generate":
                if (Files.exists(DATASET))
                    throw new IllegalStateException(
                            "Dataset already generated; keep its stable IDs and dates");
                generate();
                break;
            case "apply":
                apply();
                break;
            case "verify":
                verify();
                break;
            default:
                throw new IllegalArgumentException("Unknown dataset operation");
        }
    }
}

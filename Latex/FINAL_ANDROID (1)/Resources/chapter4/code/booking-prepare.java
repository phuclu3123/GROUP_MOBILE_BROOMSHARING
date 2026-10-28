                                    BookingPolicy.plan(room, request, System.currentTimeMillis());
                            DocumentSnapshot channel = tx.get(db.collection("appConfig").document("payments"));
                            BookingPolicy.require("INSTANT_5M".equals(channel.getString("flow"))
                                            && room.getOwnerUid().equals(channel.getString("demoHostUid")),
                                    "Phòng này chưa hỗ trợ thanh toán demo. Bạn có thể liên hệ Host.");
                            booking.paymentFlow = "INSTANT_5M";
                            booking.id = bookingId;
                            booking.guestUid = uid;
                            booking.guestName = guest.getString("name");
                            booking.guestPhone = guest.getString("phone");
                            booking.requestFingerprint = fingerprint;
                            List<AvailabilityMonth> months = months(tx, booking);

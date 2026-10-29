                            for (int i = 0; i < months.size(); i++)
                                tx.set(
                                        db.collection("availabilityMonths")
                                                .document(booking.monthKeys.get(i)),
                                        months.get(i));
                            tx.set(ref, booking);
                            tx.update(
                                    db.collection("rooms").document(room.getId()),
                                    "activeBookingCount",
                                    room.activeBookingCount < 0 ? -1 : room.activeBookingCount + 1,
                                    "bookingRevision",
                                    FieldValue.increment(1),

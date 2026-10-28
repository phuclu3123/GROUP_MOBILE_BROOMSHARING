                .whereEqualTo(field, uid)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            // Chỉ phát dữ liệu đã xác nhận từ server, không phát cache hay ghi đang
                            // chờ.
                            if (rows != null
                                    && !rows.getMetadata().isFromCache()
                                    && !rows.getMetadata().hasPendingWrites())

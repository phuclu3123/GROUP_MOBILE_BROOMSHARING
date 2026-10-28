                .orderBy("sequence", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, e) -> {
                            if (e != null) {
                                cb.onError(e);
                                return;
